let $directory-path := ''
let $file-name := '[FILEPATH]'
let $login := xmldb:login('/db/', '[USERNAME]', '[PASSWORD]')
let $add := subversion:delete(fn:concat($directory-path,$file-name))
return
    <creation><add>{$add}</add></creation>