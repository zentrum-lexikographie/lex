from pathlib import Path

import click

import zdl.article
import zdl.structure
import zdl.typography


@click.command()
@click.argument('articles',
                required=True,
                type=click.Path(exists=True, file_okay=False, resolve_path=True))
def main(articles):
    '''
    This script does the automatic steps for the redaction process step 2
    of the DWDS newly edited entries. This comprises all formal checks and
    verifications of the data before the internal preview publication.

    See http://odo.dwds.de/twiki/bin/view/Lexikographen/RedaktionsProzess
    for detailed information on the complete redaction process.
    '''
    num_articles = sum([1 for f in zdl.article.files(Path(articles))])
    article_files = click.progressbar(
        zdl.article.files(Path(articles)),
        length=num_articles,
        width=0, label='Red-1 -> Red-2'
    )
    with article_files as articles:
        for p in articles:
            for (document, article) in zdl.article.parse(p):
                if zdl.article.has_status('Red-1', article):
                    comments = []
                    comments.extend(zdl.structure.check(article))
                    comments.extend(zdl.typography.check(article))
                    for target, comment in comments:
                        zdl.article.add_comment(target, comment, 'Redaktion2')

                    source = article.get('Quelle', '')
                    accepted_source = False
                    for accepted in ('DWDS', 'ZDL'):
                        if accepted in source:
                            accepted_source = True
                            break

                    passed = accepted_source and len(comments) == 0
                    status = 'Red-2' if passed else 'Red-2-zurückgewiesen'
                    article.set('Status', status)
                    if passed and article.get('Erstellungsdatum') is None:
                        timestamp = article.get('Zeitstempel')
                        # AG: Erstellungsdatum ist Datum der internen Redaktionsrunde,
                        # angenähert durch Übergang von Red-1 zu Red-2
                        article.set('Erstellungsdatum', timestamp)

                    zdl.article.save(document, p)


if __name__ == '__main__':
    main()
