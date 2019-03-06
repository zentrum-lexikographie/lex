let $directory-path := '[COLLECTION]/'
let $file-name := '[FILENAME]'
let $add := subversion:add(fn:concat($directory-path,$file-name))
return
    <creation>{$add}</creation>