<?xml version="1.0" encoding="utf-8"?>

<xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
  xmlns:d="http://www.dwds.de/ns/1.0"
  exclude-result-prefixes="d">
  
  <xsl:variable name="hyphenation"/> 
  <xsl:variable name="uri"/> 
  <xsl:variable name="article_no"/> 
  <xsl:variable name="has_etym"/> 
  <xsl:variable name="has_ot"/> 
  <xsl:variable name="has_gb"/> 
  <xsl:variable name="has_wp"/> 
  <xsl:variable name="has_relations"/> 
  <xsl:variable name="phonology"/> 

<xsl:output
  method="html" media-type="text/html"
  cdata-section-elements="script style"
  indent="no"
  encoding="utf-8"/>

<xsl:template match="d:Artikel">
  <div class="dwdswb-artikel">
    <xsl:element name="div">
      <xsl:attribute name="class">
        <xsl:text>dwdswb-kopf</xsl:text>
<!--        <xsl:if test="/d:Artikel[@Quelle='SMOR']">
          <xsl:text> smor</xsl:text>
        </xsl:if>-->
      </xsl:attribute>
      <!-- Schreibungen -->
      <div class="dwdswb-schreibungen">
        <span class="dwdswb-schreibungen-container">
          <xsl:for-each select="//d:Artikel/d:Formangabe/d:Schreibung">
            <xsl:variable name="current-schreibung" select="."/>
            <xsl:choose>
              <xsl:when test="position() = 1">
                <!--<span class="dwdswb-schreibung"><xsl:value-of select="."/></span>-->
                <xsl:call-template name="schreibung">
                  <xsl:with-param name="value" select="current()"/>
                </xsl:call-template>
              </xsl:when>
              <xsl:otherwise>
                <xsl:variable name="geschrieben">
                  <xsl:for-each select="preceding::d:Schreibung">
                    <xsl:if test="$current-schreibung = current()">
                      1
                    </xsl:if>
                  </xsl:for-each>
                </xsl:variable>
                <xsl:if test="string-length($geschrieben) = 0">
                  <!--<span class="dwdswb-schreibung"><xsl:value-of select="."/></span>-->
                  <xsl:call-template name="schreibung">
                    <xsl:with-param name="value" select="current()"/>
                  </xsl:call-template>
                </xsl:if>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:for-each>
        </span>
        <xsl:if test="string-length(d:Formangabe/d:Grammatik/d:Wortklasse) != 0">
          –
          <span class="dwdswb-wortklasse">
            <xsl:value-of select="d:Formangabe/d:Grammatik/d:Wortklasse"/>
          </span>
      
        </xsl:if>
      </div>
      <!-- //Schreibungen -->

      <!-- Grammatik; bei mehreren identischen Grammatik-Angaben nur die erste (Hals-Nasen-Ohrenarzt) -->
      <xsl:for-each select="//d:Artikel/d:Formangabe/d:Grammatik">
        <xsl:variable name="current-grammatik" select="."/>
        <xsl:choose>
          <xsl:when test="position() = 1">
            <xsl:apply-templates select="current()"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:variable name="grammatik_seen">
              <xsl:for-each select="preceding::d:Grammatik">
                <xsl:if test="$current-grammatik = current()">
                  1
                </xsl:if>
              </xsl:for-each>
            </xsl:variable>
            <xsl:if test="string-length($grammatik_seen) = 0">
              <xsl:apply-templates select="current()"/>
            </xsl:if>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
      <!-- //Grammatik -->

      <!-- Aussprache -->
      <xsl:choose>
        <xsl:when test="d:Formangabe/d:Aussprache">
          <div class="dwdswb-aussprache">
            Aussprache:
            <xsl:for-each select="//d:Aussprache">
              <xsl:variable name="current-aussprache" select="."/>
              <xsl:choose>
                <xsl:when test="position() = 1">
                  <xsl:call-template name="aussprache">
                    <xsl:with-param name="pathPart" select="$current-aussprache"/>
                  </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:variable name="ausgesprochen">
                    <xsl:for-each select="preceding::d:Aussprache">
                      <xsl:if test="$current-aussprache = current()">
                        1
                      </xsl:if>
                    </xsl:for-each>
                  </xsl:variable>
                  <xsl:if test="string-length($ausgesprochen) = 0">
                    <xsl:call-template name="aussprache">
                      <xsl:with-param name="pathPart" select="current()"/>
                    </xsl:call-template>
                  </xsl:if>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:for-each>
          </div>
        </xsl:when>
        <xsl:when test="$phonology">
          <div class="dwdswb-aussprache">
            Aussprache: [<xsl:value-of select="$phonology"/>]
            <span class="automatic">(computergeneriert)</span>
          </div>
        </xsl:when>
      </xsl:choose>
      <!-- //Aussprache -->

      <!-- Herkunft -->
      <xsl:if test="string-length(//d:Diachronie/d:Etymologie) != 0">
        <div class="dwdswb-etymologie">Herkunft: <xsl:apply-templates select="//d:Diachronie/d:Etymologie"/></div>
      </xsl:if>
      <!-- //Herkunft -->

      <xsl:choose>
        <!-- Wortzerlegung -->
        <xsl:when test="//d:Artikel/d:Verweise/d:Verweis[@Typ='Erstglied'] and //d:Artikel/d:Verweise/d:Verweis[@Typ='Letztglied']">
          <xsl:call-template name="global_verweise">
            <xsl:with-param name="label" select="'Wortzerlegung'"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:when test="//d:Artikel/d:Verweise/d:Verweis[@Typ='Derivation']">
          <xsl:call-template name="global_verweise">
            <xsl:with-param name="label" select="'Ableitung von'"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:when test="//d:Artikel/d:Verweise/d:Verweis[@Typ='Movierung']">
          <xsl:call-template name="global_verweise">
            <xsl:with-param name="label" select="'weibliche Form von'"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:when test="//d:Artikel/d:Verweise/d:Verweis[@Typ='Konversion']">
          <xsl:call-template name="global_verweise">
            <xsl:with-param name="label" select="'Ableitung von'"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:when test="//d:Artikel/d:Verweise/d:Verweis[@Typ='Diminutiv']">
          <xsl:call-template name="global_verweise">
            <xsl:with-param name="label" select="'Verkleinerungsform von'"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:when test="//d:Artikel/d:Verweise/d:Verweis[@Typ='Kurzform']">
          <xsl:call-template name="global_verweise">
            <xsl:with-param name="label" select="'Kurzform von'"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:when test="//d:Artikel/d:Verweise/d:Verweis[@Typ='Abkürzung']">
          <xsl:call-template name="global_verweise">
            <xsl:with-param name="label" select="'Abkürzung von'"/>
          </xsl:call-template>
        </xsl:when>
      </xsl:choose>

      <!-- Worttrennung -->
      <xsl:if test="$hyphenation and contains($hyphenation, '-')">
        <div class="dwdswb-worttrennung">
          <xsl:text>Worttrennung: </xsl:text>
          <xsl:value-of select="$hyphenation"/>
          <xsl:text> </xsl:text>
          <span class="automatic">(computergeneriert)</span>
        </div>
      </xsl:if>
      <!-- //Worttrennung -->
    </xsl:element><!-- //Kopf -->

    <nav class="navbar navbar-default dwdswb-navbar">
      <xsl:if test="//d:Artikel/d:Lesart//text()[not(normalize-space(.)='')]">
        <xsl:element name="a">
          <xsl:attribute name="href"><xsl:value-of select="$uri"/>#wb-<xsl:value-of select="$article_no"/></xsl:attribute>
          <xsl:attribute name="class">btn btn-default navbar-btn sf</xsl:attribute>
          <xsl:text>Bedeutungen</xsl:text>
        </xsl:element>
      </xsl:if>
      <xsl:if test="$has_etym = 1">
        <xsl:element name="a">
          <xsl:attribute name="href"><xsl:value-of select="$uri"/>#et-<xsl:value-of select="$article_no"/></xsl:attribute>
          <xsl:attribute name="class">btn btn-default navbar-btn sf</xsl:attribute>
          <xsl:text>Etymologie</xsl:text>
        </xsl:element>
      </xsl:if>
      <xsl:if test="$has_ot = 1">
        <xsl:element name="a">
          <xsl:attribute name="href"><xsl:value-of select="$uri"/>#ot-<xsl:value-of select="$article_no"/></xsl:attribute>
          <xsl:attribute name="class">btn btn-default navbar-btn sf</xsl:attribute>
          <xsl:text>Thesaurus</xsl:text>
        </xsl:element>
      </xsl:if>
      <xsl:if test="$has_gb >= 1">
        <xsl:element name="a">
          <xsl:attribute name="href"><xsl:value-of select="$uri"/>#gb-<xsl:value-of select="$article_no"/></xsl:attribute>
          <xsl:attribute name="class">btn btn-default navbar-btn sf</xsl:attribute>
          <xsl:text>Verwendungsbeispiel</xsl:text>
          <xsl:if test="$has_gb > 1">
            <xsl:text>e</xsl:text>
          </xsl:if>
        </xsl:element>
      </xsl:if>
      <xsl:if test="$has_wp = 1">
        <xsl:element name="a">
          <xsl:attribute name="href"><xsl:value-of select="$uri"/>#wp-<xsl:value-of select="$article_no"/></xsl:attribute>
          <xsl:attribute name="class">btn btn-default navbar-btn sf</xsl:attribute>
          <xsl:text>Typische Verbindungen</xsl:text>
        </xsl:element>
      </xsl:if>
      <xsl:if test="$has_relations = 1">
        <xsl:element name="a">
          <xsl:attribute name="href"><xsl:value-of select="$uri"/>#rel-<xsl:value-of select="$article_no"/></xsl:attribute>
          <xsl:attribute name="class">btn btn-default navbar-btn sf</xsl:attribute>
          <xsl:text>Wortbildung</xsl:text>
        </xsl:element>
      </xsl:if>
    </nav>

    <!-- Lesarten -->
    <xsl:if test="//d:Artikel/d:Lesart//text()[not(normalize-space(.)='')]">
      <div class="dwdswb-lesarten">
        <xsl:element name="a">
          <xsl:attribute name="name">wb-<xsl:value-of select="$article_no"/></xsl:attribute>
        </xsl:element>
        <!-- Wörterbuchquelle -->
        <div class="dwdswb-quelle">
          <xsl:choose>
            <xsl:when test="@Quelle = 'Duden_1999'">
              <abbr title="Duden, Das große Wörterbuch der deutschen Sprache, Mannheim 1999">Duden, GWDS, 1999</abbr>
            </xsl:when>
            <xsl:otherwise>
              <xsl:element name="abbr">
                <xsl:attribute name="title">
                  <xsl:call-template name="typ_lang"/>
                </xsl:attribute>
                <xsl:choose>
                  <xsl:when test="@Quelle = 'WDG' and @Typ = 'Vollartikel'">
                    <xsl:text>eWDG</xsl:text>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="@Quelle"/>
                    <xsl:text> (</xsl:text><xsl:value-of select="@Typ"/><xsl:text>)</xsl:text>
                  </xsl:otherwise>
                </xsl:choose>
                <xsl:text>, </xsl:text>
                <xsl:value-of select="substring(@Zeitstempel,1,4)"/>
              </xsl:element>
            </xsl:otherwise>
          </xsl:choose>
        </div>
        <!-- //Wörterbuchquelle -->
        <h4>Bedeutungen</h4>
        <xsl:apply-templates select="//d:Artikel/d:Lesart"/>
      </div>
      <!-- //Lesarten -->
    </xsl:if>
  </div>
</xsl:template>

<xsl:template name="schreibung">
  <xsl:param name="value"/>
  <xsl:element name="span">
    <xsl:attribute name="class">
      dwdswb-schreibung
      <xsl:choose>
        <xsl:when test="@Typ = 'U' or @Typ = 'U_U'">
          dwdswb-schreibung-ungueltig
        </xsl:when>
      </xsl:choose>
    </xsl:attribute>
    <xsl:value-of select="$value"/>
    <xsl:choose>
      <xsl:when test="@Typ = 'U'">
        <xsl:text> (ungültig)</xsl:text>
      </xsl:when>
      <xsl:when test="@Typ = 'U_U'">
        <xsl:text> (war und ist ungültig)</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:element>
</xsl:template>

<xsl:template name="aussprache">
  <xsl:param name="pathPart"/>
  <span style="cursor:pointer" onclick="$(this).next('audio').trigger('play');">
    <img src="http://icons.iconarchive.com/icons/icons8/windows-8/16/Mobile-Speaker-icon.png" style="vertical-align:middle;opacity:.6" />
  </span>
  <audio>
    <xsl:element name="source">
      <xsl:attribute name="type">audio/mpeg</xsl:attribute>
      <xsl:attribute name="src">http://media.dwds.de/dwds2/audio/<xsl:value-of select="$pathPart"/>.mp3</xsl:attribute>
    </xsl:element>
    <span title="Ihr Browser unterstützt kein Audio-Element.">Fehler</span>
  </audio>
  <xsl:text> </xsl:text>
</xsl:template>

<!-- Artikeltypen -->
<xsl:template name="typ_lang">
  <xsl:choose>
    <xsl:when test="@Quelle = 'WDG'">
      <xsl:choose>
        <xsl:when test="@Typ = 'Vollartikel'">
          Lexikographisch voll bearbeiteter Artikel aus dem Wörterbuch der deutschen Gegenwartssprache
        </xsl:when>
        <xsl:when test="@Typ = 'Minimalartikel'">
          Minimalartikel aus dem Wörterbuch der deutschen Gegenwartssprache
        </xsl:when>
      </xsl:choose>
    </xsl:when>
    <xsl:when test="@Quelle = 'DWDS'">
      <xsl:choose>
        <xsl:when test="@Typ = 'Vollartikel'">
          Vom DWDS-Team lexikographisch voll bearbeiteter Artikel
        </xsl:when>
        <xsl:when test="@Typ = 'Basisartikel'">
          Vom DWDS-Team bearbeiteter Artikel mit einer eingeschränkten Menge lexikographischer Informationen
        </xsl:when>
      </xsl:choose>
    </xsl:when>
  </xsl:choose>
</xsl:template>
<!-- //Artikeltypen -->

<xsl:template name="global_verweise">
  <xsl:param name="label"/>
  <div>
    <xsl:value-of select="$label"/>:
    <xsl:for-each select="//d:Artikel/d:Verweise/d:Verweis">
      ↗<xsl:element name="a">
        <xsl:attribute name="href">/wb/<xsl:value-of select="current()/d:Ziellemma"/><xsl:if test="current()/d:Ziellemma/@hidx">#<xsl:value-of select="current()/d:Ziellemma/@hidx"/></xsl:if></xsl:attribute>
        <xsl:value-of select="current()/d:Ziellemma"/><xsl:if test="current()/d:Ziellemma/@hidx"><sup><xsl:value-of select="current()/d:Ziellemma/@hidx"/></sup></xsl:if>
      </xsl:element>
    </xsl:for-each>
  </div>
</xsl:template>

<!-- Lesarten -->
<xsl:template match="d:Lesart">
  <div class="dwdswb-lesart">
    <div class="dwdswb-lesart-n">
      <xsl:value-of select="@n"/>
    </div>
    <div class="dwdswb-lesart-content">
      <div class="dwdswb-lesart-def">
        <xsl:if test="string-length(d:Grammatik)">
          <xsl:apply-templates select="d:Grammatik"/>
        </xsl:if>
        <xsl:if test="string-length(d:Syntagmatik/d:Phrasem)"> <!-- "Staatssicherheit" -->
          <xsl:apply-templates select="d:Syntagmatik/d:Phrasem"/>
        </xsl:if>
        <xsl:if test="not(string-length(d:Syntagmatik/d:Phrasem)) and string-length(d:Syntagmatik)">
          <xsl:apply-templates select="d:Syntagmatik"/>
        </xsl:if>
        <xsl:if test="string-length(d:Diasystematik)">
          <xsl:apply-templates select="d:Diasystematik"/>
        </xsl:if>
        <xsl:if test="string-length(d:Pragmatik)">
          <xsl:apply-templates select="d:Pragmatik"/>
        </xsl:if>
        <xsl:apply-templates select="d:Formangabe" mode="lesart-formangabe"/>
        <span class="dwdswb-definitionen">
          <xsl:apply-templates select="d:Definition"/>
        </span>
        <xsl:if test="string-length(d:Kollokationen)">
          <xsl:apply-templates select="d:Kollokationen"/>
        </xsl:if>
        <xsl:apply-templates select="d:Verweise"/>
      </div>
      <xsl:apply-templates select="d:Verwendungsbeispiele"/>
      <xsl:apply-templates select="d:Lesart"/>
    </div>
  </div>
</xsl:template>
<!-- //Lesarten -->

<xsl:template match="d:Schreibung"/>

<xsl:template match="d:Formangabe"/>

<xsl:template match="d:Diachronie"/>

<xsl:template match="d:Formangabe" mode="lesart-formangabe">
  <div class="dwdswb-formangabe">
    <xsl:apply-templates/>
  </div>
</xsl:template>

<xsl:template match="d:Artikel/d:Formangabe/d:Grammatik">
  <div class="dwdswb-grammatik">
    <xsl:apply-templates/>
    <xsl:choose>
      <xsl:when test="d:Wortklasse = 'Verb'">
        <!--
          Auxiliar*
          reflexiv?
          Praesens*
          Praeteritum*
          Partizip_II*
        -->
        <xsl:if test="d:reflexiv">
          reflexiv
        </xsl:if>
        <span class="dwdswb-flexionen">
          <xsl:if test="d:Praesens">
            <span class="dwdswb-flexion">
              <xsl:value-of select="d:Praesens"/>
            </span>
          </xsl:if>
          <xsl:if test="d:Praeteritum">
            <span class="dwdswb-flexion">
              <xsl:value-of select="d:Praeteritum"/>
            </span>
          </xsl:if>
          <xsl:if test="d:Partizip_II">
            <span class="dwdswb-flexion">
              <xsl:if test="d:Auxiliar">
                <xsl:for-each select="d:Auxiliar">
                  <xsl:value-of select="current()"/>
                  <xsl:if test="position() != last()">
                    <xsl:text>/</xsl:text>
                  </xsl:if>
                </xsl:for-each>
                <xsl:text> </xsl:text>
              </xsl:if>
              <xsl:for-each select="d:Partizip_II">
                <xsl:value-of select="current()"/>
                <xsl:if test="position() != last()">
                  <xsl:text>/</xsl:text>
                </xsl:if>
              </xsl:for-each>
            </span>
          </xsl:if>
        </span>
      </xsl:when>
      <xsl:when test="d:Wortklasse = 'Substantiv'">
        <!--
          Genus ?
          Flexionsformen_Nomen
          Numeruspraeferenz ?
          Artikelpraeferenz ?
          allgemeine_grammatische_Angaben ?
        -->

        <!--
          Genus = element Genus { 'mask.' | 'fem.' | 'neutr.' }
        -->
        <xsl:if test="string-length(../d:Diasystematik)">
          <xsl:apply-templates select="../d:Diasystematik"/>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="d:Genus = 'mask.'">
            <xsl:text>Maskulinum</xsl:text>
          </xsl:when>
          <xsl:when test="d:Genus = 'fem.'">
            <xsl:text>Femininum</xsl:text>
          </xsl:when>
          <xsl:when test="d:Genus = 'neutr.'">
            <xsl:text>Neutrum</xsl:text>
          </xsl:when>
        </xsl:choose>
        <xsl:for-each select="d:Genitiv">
          <xsl:if test="position() = 1">
            <xsl:text>, </xsl:text>
          </xsl:if>
          <xsl:value-of select="current()"/>
          <xsl:if test="position() != last()">
            <xsl:text>/</xsl:text>
          </xsl:if>
        </xsl:for-each>
        <xsl:for-each select="d:Plural">
          <xsl:if test="position() = 1">
            <xsl:text>, </xsl:text>
          </xsl:if>
          <xsl:value-of select="current()"/>
          <xsl:if test="position() != last()">
            <xsl:text>/</xsl:text>
          </xsl:if>
        </xsl:for-each>
      </xsl:when>
    </xsl:choose>
  </div>
</xsl:template>

<xsl:template match="d:Artikel/d:Formangabe/d:Grammatik/*"/>

<xsl:template match="d:Lesart/d:Formangabe/d:Grammatik">
  <span class="dwdswb-grammatik"><xsl:apply-templates/></span>
</xsl:template>

<!-- Verben -->
<xsl:template match="d:Lesart/d:Formangabe/d:Grammatik/d:Auxiliar">
  <xsl:if test="not(string-length(following-sibling::d:Partizip_II))">
    mit Hilfsverb
    <xsl:text> </xsl:text>
  </xsl:if>
  ›<xsl:apply-templates/>‹
</xsl:template>

<xsl:template match="d:Lesart/d:Formangabe/d:Grammatik/d:Praesens">
  <xsl:apply-templates/>
  <xsl:text> </xsl:text>
  <span style="font-style:italic">(Praesens)</span>
</xsl:template>
<!-- //Verben -->

<xsl:template match="d:Wortklasse"/>

<xsl:template match="d:Wortbildung">
  <div class="dwdswb-wortbildung">
    <xsl:apply-templates/>
  </div>
</xsl:template>

<xsl:template match="d:Kasuspraeferenz">
  <span class="dwdswb-kasuspraeferenz">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Funktionspraeferenz">
  <span class="dwdswb-funktionspraeferenz">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Einschraenkung">
  <span class="dwdswb-einschraenkung">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Definition">
  <span class="dwdswb-definition">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Definition[@Typ='Spezifizierung']">
  <span class="dwdswb-definition-spezifizierung">
    <xsl:apply-templates/>
  </span>
  <xsl:text> </xsl:text>
</xsl:template>

<xsl:template match="d:Kollokationen">
  <div class="dwdswb-kollokationen">
    <xsl:for-each select="d:Kollokation2">
      <xsl:apply-templates select="current()"/>
    </xsl:for-each>
  </div>
</xsl:template>

<xsl:template match="d:Kollokation2">
  <div class="dwdswb-kollokation">
    <xsl:if test="string-length(@syntaktische_Relation)">
      <span class="dwdswb-relation">
        <xsl:call-template name="syntaktische_relation">
          <xsl:with-param name="value" select="@syntaktische_Relation"/>
        </xsl:call-template>
        <xsl:text>: </xsl:text>
      </span>
    </xsl:if>
    <xsl:apply-templates select="d:Belegtext"/>
  </div>
</xsl:template>

<xsl:template name="syntaktische_relation">
  <xsl:param name="value"/>
  <xsl:choose>
    <xsl:when test="$value = 'Stichwort_ist_Adjektivattribut_von'">
      <xsl:text>als Adjektivattribut</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Adverbialbestimmung_von'">
      <xsl:text>als Adverbialbestimmung</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Akkusativobjekt_von'">
      <xsl:text>als Akkusativobjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Aktivsubjekt_von'">
      <xsl:text>als Aktivsubjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Dativobjekt_von'">
      <xsl:text>als Dativobjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Genitiv-/Akkusativ-/Dativobjekt_von'">
      <xsl:text>als Genitiv-/Akkusativ-/Dativobjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Genitivattribut_von'">
      <xsl:text>als Genitivattribut</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_in_Koordination_mit'">
      <xsl:text>in Koordination</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_in_Präpositionalgruppe/-objekt_zu'">
      <xsl:text>in Präpositionalgruppe/-objekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_in_vergleichender_Wort-/Nominalgruppe_zu'">
      <xsl:text>in vergleichender Wort-/Nominalgruppe</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_partitive_Apposition'">
      <xsl:text>als partitive Apposition</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Passivsubjekt_von'">
      <xsl:text>als Passivsubjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Prädikativ_von'">
      <xsl:text>als Prädikativ</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_ist_Subjekt_von_(Aktiv/Passiv)'">
      <xsl:text>als Aktiv-/Passivsubjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Adjektivattribut'">
      <xsl:text>mit Adjektivattribut</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Adverbialbestimmung'">
      <xsl:text>mit Adverbialbestimmung</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Akkusativobjekt'">
      <xsl:text>mit Akkusativobjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Aktivsubjekt'">
      <xsl:text>mit Aktivsubjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Dativobjekt'">
      <xsl:text>mit Dativobjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Genitiv-/Akkusativ-/Dativobjekt'">
      <xsl:text>mit Genitiv-/Akkusativ-/Dativobjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Genitivattribut'">
      <xsl:text>mit Genitivattribut</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Passivsubjekt'">
      <xsl:text>mit Passivsubjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Prädikativ'">
      <xsl:text>mit Prädikativ</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Präpositionalgruppe/-objekt_mit'">
      <xsl:text>in Präpositionalgruppe/-objekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Subjekt_(Aktiv/Passiv)'">
      <xsl:text>mit Aktiv-/Passivsubjekt</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_Verbzusatz'">
      <xsl:text>mit Verbzusatz</xsl:text>
    </xsl:when>
    <xsl:when test="$value = 'Stichwort_hat_vergleichende_Wort-/Nominalgruppe_mit'">
      <xsl:text>mit vergleichender Wort-/Nominalgruppe</xsl:text>
    </xsl:when>
  </xsl:choose>
</xsl:template>

<xsl:template match="d:Verweise">
  <div class="dwdswb-verweise">
    <xsl:for-each select="d:Verweis">
      <xsl:apply-templates select="."/>
    </xsl:for-each>
  </div>
</xsl:template>

<xsl:template match="d:Verweis">
  <xsl:choose>
    <xsl:when test="name(../parent::*) = 'Artikel'">
    </xsl:when>
    <xsl:otherwise>
      <span class="dwdswb-verweis">
        ↗<xsl:element name="a">
          <xsl:attribute name="href">/wb/<xsl:value-of select="d:Ziellemma"/><xsl:if test="d:Ziellemma/@hidx">#<xsl:value-of select="d:Ziellemma/@hidx"/></xsl:if></xsl:attribute>
          <xsl:value-of select="d:Ziellemma"/><xsl:if test="d:Ziellemma/@hidx"><sup><xsl:value-of select="d:Ziellemma/@hidx"/></sup></xsl:if>
        </xsl:element>
        <!--<xsl:value-of select="d:Ziellesart"/>-->
      </span>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template match="d:Verwendungsbeispiele">
  <div class="dwdswb-verwendungsbeispiele">
    <xsl:for-each select="d:Kompetenzbeispiel">
      <xsl:apply-templates select="current()"/>
    </xsl:for-each>
    <xsl:for-each select="d:Beleg[@class='good_example']">
      <xsl:apply-templates select="current()"/>
    </xsl:for-each>
    <xsl:for-each select="d:Beleg[not(@class)]">
      <xsl:apply-templates select="current()"/>
    </xsl:for-each>
    <xsl:for-each select="d:Beleg[@class='ungewöhnlich']">
      <xsl:apply-templates select="current()"/>
    </xsl:for-each>
  </div>
</xsl:template>

<xsl:template match="d:Kompetenzbeispiel">
  <div class="dwdswb-kompetenzbeispiel">
    <xsl:apply-templates/>
  </div>
</xsl:template>

<xsl:template match="d:Beleg">
  <div class="dwdswb-beleg">
    <xsl:apply-templates/>
  </div>
</xsl:template>

<xsl:template match="d:Belegtext">
  <span class="dwdswb-belegtext">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Autorenzusatz">
  <span class="dwdswb-autorenzusatz">
    <xsl:text>[</xsl:text>
    <xsl:apply-templates/>
    <xsl:text>]</xsl:text>
  </span>
</xsl:template>

<xsl:template match="d:Paraphrase">
  <span class="dwdswb-paraphrase">(<xsl:apply-templates/>)</span>
</xsl:template>

<!-- Fundstellen -->
<xsl:template match="d:Fundstelle">
  <span class="dwdswb-fundstelle">
    <xsl:choose>
      <xsl:when test="string-length(normalize-space(text())) > 0">
        <xsl:text>[</xsl:text><xsl:value-of select="normalize-space(.)"/><xsl:text>]</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>[</xsl:text>
        <xsl:if test="string-length(d:Autor)">
          <span class="dwdswb-fundstelle-autor"><xsl:value-of select="d:Autor"/></span>
        </xsl:if>
        <xsl:if test="string-length(d:Zusatz)">
          <span class="dwdswb-fundstelle-zusatz"><xsl:value-of select="d:Zusatz"/></span>
        </xsl:if>
        <xsl:if test="string-length(d:Titel)">
          <span class="dwdswb-fundstelle-titel"><xsl:value-of select="d:Titel"/></span>
        </xsl:if>
        <xsl:if test="string-length(d:Stelle)">
          <span class="dwdswb-fundstelle-stelle"><xsl:value-of select="d:Stelle"/></span>
        </xsl:if>
        <xsl:if test="string-length(d:Datum)">
          <span class="dwdswb-fundstelle-datum"><xsl:value-of select="d:Datum"/></span>
        </xsl:if>
        <xsl:text>]</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </span>
</xsl:template>

<xsl:template match="d:Fundstelle/d:Sigle"/>
<!-- //Fundstellen -->

<xsl:template match="d:Stichwort">
  <span class="dwdswb-stichwort">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Phrasem">
  <span class="dwdswb-phrasem smaller">
    <xsl:text>Phrasem: </xsl:text>
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Syntagmatik">
  <div class="dwdswb-syntagmatik">
    <xsl:for-each select="d:Konstruktionsmuster">
      <span class="dwdswb-konstruktionsmuster"><xsl:apply-templates/></span>
      <xsl:if test="position() != last()">
        <br />
      </xsl:if>
    </xsl:for-each>
  </div>
</xsl:template>

<xsl:template match="d:Diasystematik">
  <xsl:if test="string-length(.)">
    <xsl:for-each select="*">
      <span class="dwdswb-diasystematik"><xsl:apply-templates select="current()"/></span>
      <xsl:if test="position() != last()">
        <xsl:text>, </xsl:text>
      </xsl:if>
    </xsl:for-each>
    <xsl:text> </xsl:text>
  </xsl:if>
</xsl:template>

<xsl:template match="d:Sprachraum">
  <span class="dwdswb-sprachraum">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Stilebene">
  <span class="dwdswb-stilebene">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Stilfaerbung">
  <span class="dwdswb-stilfaerbung">
    <xsl:apply-templates/>
  </span>
</xsl:template>

<xsl:template match="d:Loeschung | d:Streichung">
  <xsl:text>[&#x2026;]</xsl:text>
</xsl:template>

<!-- DEPRECATED
<xsl:template match="d:Pragmatik">
  <span class="dwdswb-pragmatik">
    <xsl:apply-templates/>
  </span><xsl:text> </xsl:text>
</xsl:template>
-->

</xsl:stylesheet>
