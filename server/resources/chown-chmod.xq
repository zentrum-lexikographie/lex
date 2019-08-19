let $login := xmldb:login('%s','%s','%s')

let $resource := '%s'
let $permissions := '%s'
let $group := '%s"

return (sm:chmod($resource, $permissions), sm:chgrp($resource, $group))