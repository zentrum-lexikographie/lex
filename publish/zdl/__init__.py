import logging

import coloredlogs

coloredlogs.install(level=logging.INFO)

logger = logging.getLogger(__name__)
