let $login := xmldb:login('/db/dwdswb/data','[USER]','[PASSWORD]')

let $resource := "[RESOURCE]"
let $permissions := "[PERMISSIONS]"
let $group := "[GROUP]"

return
	(sm:chmod($resource, $permissions),
	 sm:chgrp($resource, $group))