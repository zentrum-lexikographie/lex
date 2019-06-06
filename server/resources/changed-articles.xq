xquery version '3.0';
declare namespace lex='http://www.dwds.de/ns/1.0';
for $article in /lex:DWDS/lex:Artikel
let $doc := fn:root($article)
let $modified := xmldb:last-modified(util:collection-name($doc),util:document-name($doc))
where $modified >= xs:dateTime('%s')
return (<doc><uri>{fn:document-uri($doc)}</uri><modified>{$modified}</modified></doc>)