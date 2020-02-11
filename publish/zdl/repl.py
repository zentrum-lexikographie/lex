import click
import datetime
import sys

from parsimonious.exceptions import ParseError
from parsimonious.grammar import Grammar
from parsimonious.nodes import NodeVisitor

from prompt_toolkit import PromptSession

from tabulate import tabulate

import zdl.lex

repl_grammar = Grammar("""
cmd = status / locks / exit
status = "status"
locks = "locks"
exit = "exit" / "quit"
""")


class REPLEvaluator(NodeVisitor):
    def __init__(self, session):
        self.session = session

    def visit_status(self, node, visited_children):
        status = self.session.server.status()
        return tabulate(
            [['server', self.session.server.base_url],
             ['user', status['user']]],
            headers=['key', 'value'],
            tablefmt='psql'
        )

    def visit_locks(self, node, visited_children):
        locks = []
        for lock in self.session.server.locks():
            locks.append([
                lock['resource'],
                lock['owner'],
                datetime.datetime.fromtimestamp(lock['expires'] / 1000)
            ])

        return tabulate(
            locks,
            headers=['Resource', 'Owner', 'Expires'],
            tablefmt='psql'
        )

    def visit_exit(self, node, visited_children):
        sys.exit(0)

    def visit_cmd(self, node, visited_children):
        cmd, = visited_children
        return cmd

    def generic_visit(self, node, visited_children):
        return visited_children or node


class REPLSession:
    def __init__(self, server):
        self.prompts = PromptSession()
        self.server = server
        self.server_str = str(server)

    def _read(self):
        return self.prompts.prompt(
            [('bold', '>'), ('', ' ')],
            rprompt=[('italic', self.server_str)]
        )

    def _eval(self, input):
        try:
            return REPLEvaluator(self).visit(repl_grammar.parse(input))
        except ParseError as e:
            print(e)

    def _print(self, output):
        if output:
            print(output)

    def interactive(self):
        try:
            self._print(self._eval("status"))
            while True:
                self._print(self._eval(self._read()))
        except EOFError:
            pass

    def batch(self, cmds):
        for cmd in cmds:
            self._print(self._eval(cmd))


@click.command()
@click.option('--server-base',
              envvar='ZDL_LEX_SERVER_BASE',
              default='https://lex.dwds.de/')
@click.option('--server-user',
              envvar='ZDL_LEX_SERVER_USER')
@click.option('--server-password',
              envvar='ZDL_LEX_SERVER_PASSWORD')
@click.option('--server-token',
              envvar='ZDL_LEX_SERVER_TOKEN')
@click.argument('commands', nargs=-1)
def main(server_base, server_user, server_password, server_token, commands):
    server_auth = (server_user, server_password) if server_user else None
    server = zdl.lex.Server(server_base, server_auth, server_token)
    session = REPLSession(server)

    if len(commands or []) > 0:
        session.batch(commands)
    else:
        session.interactive()


if __name__ == '__main__':
    main()
