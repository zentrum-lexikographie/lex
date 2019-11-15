from multiprocessing import Pool

import zdl.article
import zdl.sanity
import zdl.structure
import zdl.typography


def annotate_file(p):
    for (document, article) in zdl.article.parse(p):
        if zdl.article.has_status('Red-1', article):
            comments = []
            comments.extend(zdl.sanity.check(article))
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
    return p


def perform(articles):
    '''
    This script does the automatic steps for the redaction process step 2
    of the DWDS newly edited entries. This comprises all formal checks and
    verifications of the data before the internal preview publication.

    See http://odo.dwds.de/twiki/bin/view/Lexikographen/RedaktionsProzess
    for detailed information on the complete redaction process.
    '''
    with Pool() as pool:
        for r in pool.imap_unordered(annotate_file, articles):
            pass
