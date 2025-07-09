import click
import datetime
import sys
import uuid

from parsimonious.exceptions import ParseError
from parsimonious.grammar import Grammar
from parsimonious.nodes import NodeVisitor

from prompt_toolkit import PromptSession

from tabulate import tabulate

import zdl.lex
import zdl.log

grammar = Grammar("""
cmd = status / lock / unlock / git_cmd / exit

status = "status"

unlock = "unlock" ws+ str_val
lock = ~"locks?" (ws+ str_val)? (ws+ int_val)?

git_cmd = "git" ws+ git_cd
git_cd = "cd" ws+ str_val

exit = "exit" / "quit"

str_val = quoted_str / unquoted_str
quoted_str = ~"['\\"]([^\\"]*)['\\"]"
unquoted_str = ~"[^\\s]+"
int_val = ~"[0-9]+"

ws = ~"\\s"
""")


class REPL(NodeVisitor):
    def __init__(self, server, server_token):
        self.prompts = PromptSession()
        self.server = server
        self.server_str = str(server)
        self.server_token = server_token

    def read(self, input=None):
        return grammar.parse(input or self.prompts.prompt(
            [('bold', '>'), ('', ' ')],
            rprompt=[('italic', self.server_str)]
        ))

    def eval(self, input):
        return self.visit(input)

    def prn(self, output=None):
        if (output is not None) and (len(output) > 0):
            print(output)

    def read_eval_print(self, input=None):
        self.prn(self.eval(self.read(input)))

    def execute(self, input=[]):
        for cmd in input:
            self.read_eval_print(cmd)
        if len(input) > 0:
            return
        try:
            while True:
                try:
                    self.read_eval_print()
                except ParseError as e:
                    print(e)
        except EOFError:
            pass

    def visit_status(self, node, visited_children):
        status = self.server.status()
        return tabulate(
            [['server', self.server.base_url],
             ['user', status['user']]],
            headers=['key', 'value'],
            tablefmt='psql'
        )

    def visit_lock(self, node, visited_children):
        _, path, ttl = visited_children
        if path is not None:
            ttl = ttl or 300
            return ["lock", path, ttl]
        else:
            locks = [
                [lock['resource'], lock['owner'],
                 datetime.datetime.fromtimestamp(lock['expires'] / 1000)]
                for lock in self.server.locks()
            ]
            return tabulate(
                locks,
                headers=['Resource', 'Owner', 'Expires'],
                tablefmt='psql'
            )

    def visit_unlock(self, node, visited_children):
        _, _, path = visited_children
        return ["unlock", path]

    def visit_git_cd(self, node, visited_children):
        _, _, path = visited_children
        return ["git", "cd", path]

    def visit_exit(self, node, visited_children):
        sys.exit(0)

    def visit_quoted_str(self, node, _):
        return node.match.group(1)

    def visit_unquoted_str(self, node, _):
        return node.text

    def visit_int_val(self, node, _):
        return int(node.text)

    def visit_ws(self, node, _):
        return None

    def generic_visit(self, node, visited_children):
        visited_children = list(filter(
            lambda c: c is not None, visited_children
        ))
        num_children = len(visited_children)
        if num_children == 0:
            return None
        elif num_children == 1:
            return visited_children[0]
        else:
            return visited_children


@click.command()
@click.option('--server-base',
              envvar='ZDL_LEX_SERVER_BASE',
              default='https://labor.dwds.de/')
@click.option('--server-user',
              envvar='ZDL_LEX_SERVER_USER')
@click.option('--server-password',
              envvar='ZDL_LEX_SERVER_PASSWORD')
@click.option('--server-token',
              envvar='ZDL_LEX_SERVER_TOKEN',
              default=(lambda: str(uuid.uuid1())))
@click.option('--debug/--no-debug',
              envvar='ZDL_DEBUG',
              default=False)
@click.argument('commands', nargs=-1)
def main(server_base, server_user, server_password, server_token,
         debug, commands):
    zdl.log.debug(debug)
    server_auth = (server_user, server_password) if server_user else None
    server = zdl.lex.Server(server_base, server_auth)
    REPL(server, server_token).execute(commands or [])


if __name__ == '__main__':
    main()
