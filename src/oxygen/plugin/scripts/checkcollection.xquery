let $login := xmldb:login('/db/dwdswb/data','admin','test123')
let $availability := xmldb:collection-available('[COLLECTION]')

return
    $availability