
declare namespace s="http://www.dwds.de/ns/1.0";

for $hit in collection(/db/dwdswb/data)//s:Artikel[@xml:id='[ID]']
return (<hit><file>{fn:base-uri($hit)}</file><id>{string($hit/@xml:id)}</id>{$hit/s:Formangabe/s:Schreibung}{$hit/descendant::s:Definition}</hit>)