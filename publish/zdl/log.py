import logging
import http.client

import coloredlogs

httpclient_logger = logging.getLogger("http.client")


def httpclient_log(*args):
    httpclient_logger.log(logging.DEBUG, " ".join(args))


http.client.print = httpclient_log
http.client.HTTPConnection.debuglevel = 1


coloredlogs.install()


def logger(name='zdl'):
    return logging.getLogger(name)


def debug(debug=True):
    level = logging.DEBUG if debug else logging.INFO
    coloredlogs.install(level)
