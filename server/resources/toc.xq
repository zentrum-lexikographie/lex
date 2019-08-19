xquery version '3.0';
for $doc in fn:collection('%s')
let $modified := xmldb:last-modified(util:collection-name($doc),util:document-name($doc))
return (<doc><uri>{fn:document-uri($doc)}</uri><modified>{$modified}</modified></doc>)