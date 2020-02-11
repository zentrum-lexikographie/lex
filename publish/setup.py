from setuptools import setup, find_packages

setup(
    name="zdl_lex_tools",
    packages=find_packages(),
    install_requires=[
        'Click==7.0',
        'coloredlogs==10.0',
        'PyMySQL==0.9.3',
        'SQLAlchemy==1.3.11',
        'gitpython==3.0.4',
        'lxml==4.4.1',
        'parsimonious==0.8.1',
        'prompt-toolkit==3.0.3',
        'pytest==5.1.3',
        'python-baseconv==1.2.2',
        'pytz==2019.2',
        'requests==2.22.0',
        'tabulate==0.8.6'
    ]
)
