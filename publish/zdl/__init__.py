import click
import logging


class LogHandler(logging.Handler):
    colors = {
        'error': dict(bold=True),
        'exception': dict(fg='red', bold=True),
        'critical': dict(fg='red', bold=True),
        'debug': dict(fg='blue'),
        'warning': dict(fg='yellow')
    }

    def emit(self, record):
        try:
            msg = self.format(record)
            level = record.levelname.lower()
            if level in self.colors:
                click.secho(msg, err=True, **self.colors[level])
            else:
                click.echo(msg, err=True)
        except Exception:
            self.handleError(record)


handler = LogHandler()
handler.setFormatter(logging.Formatter(
    '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
))

logger = logging.getLogger('zdl')
logger.addHandler(handler)
logger.setLevel(logging.INFO)
