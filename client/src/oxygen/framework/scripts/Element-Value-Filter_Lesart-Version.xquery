
declare namespace s="http://www.dwds.de/ns/1.0";

for $hit in collection([COLLECTION])//s:Lesart
    [FILTER]
return (<hit><file>{fn:base-uri($hit)}</file><id>{string($hit/@xml:id)}</id><level>{count($hit/ancestor::s:Lesart )}</level>{$hit/ancestor::s:Artikel/s:Formangabe/s:Schreibung}<dia>{string-join($hit/s:Diasystematik/*/text(), ', ')}</dia>{$hit/s:Definition}</hit>)



/ancestor::s:Artikel/s:Formangabe/s:Schreibung:
/s:Diasystematik/s:Bedeutungsebene:bildlich,übertragen,sprichwörtlich,biblisch
/s:Diasystematik/s:Stilebene:dichterisch,gehoben,umgangssprachlich,salopp,vulgär
/s:Diasystematik/s:Stilfaerbung:abwertend,altertümelnd,derb,gespreizt,höflich,papierdeutsch,scherzhaft,Schimpfwort,spöttisch,übertrieben,verhüllend,vertraulich
/s:Diasystematik/s:Gebrauchszeitraum:historisch,DDR,nazistisch,veraltet,veraltend
/s:Diasystematik/s:Sprachraum:berlinisch,besonders berlinisch,landschaftlich,mecklenburgisch,besonders mecklenburgisch,mitteldeutsch,besonders mitteldeutsch,norddeutsch,besonders norddeutsch,nordostdeutsch,besonders nordostdeutsch,nordwestdeutsch,besonders nordwestdeutsch,österreichisch,besonders österreichisch,ostmitteldeutsch,besonders ostmitteldeutsch,schweizerisch,besonders schweizerisch,süddeutsch,besonders süddeutsch,südwestdeutsch,besonders südwestdeutsch,westmitteldeutsch,besonders westmitteldeutsch
/s:Diasystematik/s:Gruppensprache:Bergmannssprache,Fliegersprache,Jägersprache,Kaufmannssprache,Kindersprache,Schülersprache,Seemannssprache,Soldatensprache,Studentensprache
/s:Diasystematik/s:Fachgebiet:fachsprachlich,dialektischer Materialismus,Druckerei,Gießerei,Hüttenwesen,Kybernetik,Marxismus,Metallurgie,politische ,Schiffbau,Schneiderei,Tabakwarenindustrie,Textilindustrie,Wissenschaft,Anthropologie,Archäologie,Astronomie,Biologie,Bakteriologie,Botanik,Sexualkunde,Verhaltensforschung,Zoologie,Chemie,Ethnologie,Geografie,Geologie,Kunstgeschichte,Mathematik,Geometrie,Logik,Statistik,Medizin,Anatomie,Pharmazie,Physiologie,Sportmedizin,Tiermedizin,Zahnmedizin,Meereskunde,Meteorologie,Paläontologie,Philosophie,Physik,Geophysik,Kernphysik,Mechanik,Optik,Psychologie,Soziologie',Sprachwissenschaft,Theologie,Wirtschaft,Bergbau,Fischerei,Forstwirtschaft,Jagdwesen,Landwirtschaft,Gartenbau,Imkerei,Tierzucht,Weinbau,Wasserwirtschaft,Bauwesen,Handwerk,Industrie,Finanzwirtschaft,Bank,Börse,Buchwesen,Geldwesen,Versicherungswesen,Rentenversicherung,Sozialversicherung,Gastronomie,Handel,Hauswirtschaft,Hotelwesen,Informationstechnologie,Technik,Elektrotechnik,Telekommunikation,Transportwesen,Post,Logistik,Verkehr,Eisenbahn,Flugwesen,Kraftfahrzeugwesen,Raumfahrt,Schifffahrt,Verlagswesen,Medien,Fernsehen,Radio,Rundfunk,Internet,Zeitungswesen,Astrologie,Handarbeit,Numismatik,Okkultismus,Philatelie,Spiel,Computerspiel,Gesellsschaftsspiel,Domino,Kartenspiel,Skat,Schach,Kinderspiel,Sport,Ballspiel,Fußball,Handball,Volleyball,Bergsport,Billard,Boxen,Eishockey,Eiskunstlauf,Eislauf,Fechten,Gymnastik,Hockey,Kegeln,Laufen,Motorsport,Radsport,Reiten,Rennen,Ringen,Rollschuhlauf,Rudern,Rugby,Schießsport,Schwimmen,Segelfliegen,Segeln,Ski,Tennis,Turnen,Kunst,Architektur,bildende Kunst,Fotografie,Film,Kochkunst,Literatur,Musik,Tanz,Theater,Varieté,Zirkus,Bildungswesen,Brauchtum,Mythologie,Religion,Politik,Diplomatie,Parlament,Militär,Polizei,Zollwesen,Steuerwesen,Jura
/s:Definition: