# ZDL/Lex – Python Tools

## Requirements

* [Python 3](https://www.python.org/)
* [ICU](http://site.icu-project.org/)

It is recommended to use a project-specific virtual environment, e.g. via
[pyenv](https://github.com/pyenv/pyenv):

```plaintext
$ pyenv virtualenv zdl-lex
$ pyenv local zdl-lex
```

## Setup

For proper Unicode string collation, we use
[PyICU](https://pypi.org/project/PyICU/) bindings. Please install the
`libicu-dev`, e.g. via

```
$ sudo apt install libicu-dev
```

Then, install Python dependencies:

```plaintext
$ pip install -e .
```
