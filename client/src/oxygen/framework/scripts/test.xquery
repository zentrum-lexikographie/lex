let $login := xmldb:login('/db/dwdswb/data', 'admin', 'test123')
let $directory-path := '/db/dwdswb/data/de/'
let $file-name := 'Debug-E_3315868.xml'
let $content := '<DWDS/>'
let $store := xmldb:store($directory-path,$file-name,$content)
let $add := subversion:add(fn:concat($directory-path,$file-name))
return
    <creation><store>{$store}</store><add></add>{$add}</creation>