xquery version "1.0";

import module namespace v = "http://exist-db.org/versioning";

let $file-path := '[FILEPATH]'
let $revision :=  '[REVISION]'
let $revisiontext := v:doc(doc($file-path),$revision)
return
    $revisiontext
