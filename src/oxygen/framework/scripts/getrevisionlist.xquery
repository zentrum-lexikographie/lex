import module namespace v = "http://exist-db.org/versioning";
let $file-path := '[FILEPATH]'
let $revisions := v:history(doc($file-path))
return
    $revisions//v:revision