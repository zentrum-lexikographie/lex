from setuptools import setup, find_packages

setup(
    name="zdl_lex_tools",
    packages=find_packages(),
    install_requires=[
        'Click==7.0',
        'gitpython==3.0.4',
        'lxml==4.4.1',
        'pytest==5.1.3',
        'python-baseconv==1.2.2',
        'pytz==2019.2',
        'requests==2.22.0'
    ]
)
