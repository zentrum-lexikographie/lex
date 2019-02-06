
declare namespace s="http://www.dwds.de/ns/1.0";

for $hit in collection('')//@Timestamp
    return (<hit><file>{fn:base-uri($hit)}</file>{$hit/ancestor::s:Artikel/s:Formangabe/s:Schreibung}</hit>)
