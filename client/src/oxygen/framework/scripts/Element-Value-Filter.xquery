
declare namespace s="http://www.dwds.de/ns/1.0";

for $hit in collection([COLLECTION])//s:Artikel
    [FILTER]
return (<hit><file>{fn:base-uri($hit)}</file><id>{string($hit/@xml:id)}</id>{$hit/s:Formangabe/s:Schreibung}{$hit/descendant::s:Definition}</hit>)
