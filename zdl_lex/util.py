import re


def monthly_release(version):
    year, month, *_ = re.findall(r"\d+", version)
    return f"{int(year):d}.{int(month):d}"
