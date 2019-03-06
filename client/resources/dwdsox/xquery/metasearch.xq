xquery version "3.0";

declare namespace s="http://www.dwds.de/ns/1.0";

for $hit in collection([COLLECTION])//s:Artikel[FILTER][RESTRICTION]/@Zeitstempel[START][END]
    order by $hit descending
    return
(<hit>
	<file>{base-uri($hit)}</file>
	<Status>{data($hit/ancestor::s:Artikel/@Status)}</Status>
	{$hit/ancestor::s:Artikel/s:Formangabe/s:Schreibung}
	<parent>{$hit/parent::node()/name()}</parent>
	<Timestamp>{data($hit)}</Timestamp>
    <CreationDate>{format-dateTime(xmldb:created(util:collection-name($hit),util:document-name($hit)), "[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]","de",(),())}</CreationDate>
	<LatestModificationDate>{format-dateTime(xmldb:last-modified(util:collection-name($hit),util:document-name($hit)), "[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]","de",(),())}</LatestModificationDate>
</hit>) 
