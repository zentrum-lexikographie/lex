
declare namespace s="http://www.dwds.de/ns/1.0";

for $hit in collection([COLLECTION])//s:Lesart
    [FILTER]
return (<hit><file>{fn:base-uri($hit)}</file><id>{string($hit/@xml:id)}</id><level>{count($hit/ancestor::s:Lesart )}</level>{$hit/ancestor::s:Artikel/s:Formangabe/s:Schreibung}<dia>{string-join($hit/s:Diasystematik/*/text(), ', ')}</dia>{$hit/s:Definition}</hit>)



/ancestor::s:Artikel/s:Formangabe/s:Schreibung:
/s:Diasystematik/s:Bedeutungsebene:bildlich,�bertragen,sprichw�rtlich,biblisch
/s:Diasystematik/s:Stilebene:dichterisch,gehoben,umgangssprachlich,salopp,vulg�r
/s:Diasystematik/s:Stilfaerbung:abwertend,altert�melnd,derb,gespreizt,h�flich,papierdeutsch,scherzhaft,Schimpfwort,sp�ttisch,�bertrieben,verh�llend,vertraulich
/s:Diasystematik/s:Gebrauchszeitraum:historisch,DDR,nazistisch,veraltet,veraltend
/s:Diasystematik/s:Sprachraum:berlinisch,besonders berlinisch,landschaftlich,mecklenburgisch,besonders mecklenburgisch,mitteldeutsch,besonders mitteldeutsch,norddeutsch,besonders norddeutsch,nordostdeutsch,besonders nordostdeutsch,nordwestdeutsch,besonders nordwestdeutsch,�sterreichisch,besonders �sterreichisch,ostmitteldeutsch,besonders ostmitteldeutsch,schweizerisch,besonders schweizerisch,s�ddeutsch,besonders s�ddeutsch,s�dwestdeutsch,besonders s�dwestdeutsch,westmitteldeutsch,besonders westmitteldeutsch
/s:Diasystematik/s:Gruppensprache:Bergmannssprache,Fliegersprache,J�gersprache,Kaufmannssprache,Kindersprache,Sch�lersprache,Seemannssprache,Soldatensprache,Studentensprache
/s:Diasystematik/s:Fachgebiet:fachsprachlich,dialektischer Materialismus,Druckerei,Gie�erei,H�ttenwesen,Kybernetik,Marxismus,Metallurgie,politische ,Schiffbau,Schneiderei,Tabakwarenindustrie,Textilindustrie,Wissenschaft,Anthropologie,Arch�ologie,Astronomie,Biologie,Bakteriologie,Botanik,Sexualkunde,Verhaltensforschung,Zoologie,Chemie,Ethnologie,Geografie,Geologie,Kunstgeschichte,Mathematik,Geometrie,Logik,Statistik,Medizin,Anatomie,Pharmazie,Physiologie,Sportmedizin,Tiermedizin,Zahnmedizin,Meereskunde,Meteorologie,Pal�ontologie,Philosophie,Physik,Geophysik,Kernphysik,Mechanik,Optik,Psychologie,Soziologie',Sprachwissenschaft,Theologie,Wirtschaft,Bergbau,Fischerei,Forstwirtschaft,Jagdwesen,Landwirtschaft,Gartenbau,Imkerei,Tierzucht,Weinbau,Wasserwirtschaft,Bauwesen,Handwerk,Industrie,Finanzwirtschaft,Bank,B�rse,Buchwesen,Geldwesen,Versicherungswesen,Rentenversicherung,Sozialversicherung,Gastronomie,Handel,Hauswirtschaft,Hotelwesen,Informationstechnologie,Technik,Elektrotechnik,Telekommunikation,Transportwesen,Post,Logistik,Verkehr,Eisenbahn,Flugwesen,Kraftfahrzeugwesen,Raumfahrt,Schifffahrt,Verlagswesen,Medien,Fernsehen,Radio,Rundfunk,Internet,Zeitungswesen,Astrologie,Handarbeit,Numismatik,Okkultismus,Philatelie,Spiel,Computerspiel,Gesellsschaftsspiel,Domino,Kartenspiel,Skat,Schach,Kinderspiel,Sport,Ballspiel,Fu�ball,Handball,Volleyball,Bergsport,Billard,Boxen,Eishockey,Eiskunstlauf,Eislauf,Fechten,Gymnastik,Hockey,Kegeln,Laufen,Motorsport,Radsport,Reiten,Rennen,Ringen,Rollschuhlauf,Rudern,Rugby,Schie�sport,Schwimmen,Segelfliegen,Segeln,Ski,Tennis,Turnen,Kunst,Architektur,bildende Kunst,Fotografie,Film,Kochkunst,Literatur,Musik,Tanz,Theater,Variet�,Zirkus,Bildungswesen,Brauchtum,Mythologie,Religion,Politik,Diplomatie,Parlament,Milit�r,Polizei,Zollwesen,Steuerwesen,Jura
/s:Definition: