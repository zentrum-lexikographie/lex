#!/usr/bin/env python3
import collections, argparse
import lxml.etree as et
import Wb

argument_parser = argparse.ArgumentParser(description='Audio file checks.')
argument_parser.add_argument('-g', '--generate',
        choices=('none', 'all', 'main'),
        default='none',
        help='generate a list of stimuli that have not yet been spoken ("all", "main" disables error logging)')
argument_parser.add_argument('-p', '--path',
        action='store_true',
        default=False,
        help='show article URIs instead of headword and snippet')

arguments = argument_parser.parse_args()

wb = Wb.Wb(strip=True)

# vorliegende Audiodateien
audiofiles = { ('/'.join(line.strip().split('/')[-2 : ])[:-4]): False
        for line in open('share/audio-file-list')
        if not '/unused/' in line and line.strip().endswith('.mp3')
}

audiofiles_basenames = set( [ a.split('/')[-1] for a in audiofiles] )

# bei Maren
lemmas_todo = set([ line.strip() for line in open ('share/lemmas-todo')
        if not line.lstrip().startswith('#') and not line.strip() == '' ])

for entry, path in wb:
    
    # only entry-level forms!
    for form in et.ETXPath('./%(Formangabe)s' % wb.TAGS)(entry):
        audios = []
        for audio in form.findall('.//%(IPA)s' % wb.TAGS):
            a = audio.get('Audiodatei', '')
            if a != '':
                audios.append(a)

        if arguments.generate == 'none':
            # check for broken audio links
            for audio in audios:
                if audio in audiofiles:
                    audiofiles[audio] = True
                else:
                    wb.report(entry, path,
                            str(wb.get_headwords(entry))+': broken audio link -- '+audio,
                            not(arguments.path)
                    )
            
            # check for dropped links
            if audios == []:
                candidates = [ wb.text(s)
                        for s in form.findall('%(Schreibung)s' % wb.TAGS)
                        if not s.get('Typ', '').startswith('U')
                ]
            
                if candidates == []:
                    continue # may happen if only U forms in //Formangabe
                candidate = candidates[0] # consider only first item
            
                for st, _ in wb.generate_stimuli(candidate, form):
                    if st in audiofiles_basenames:
                        wb.report(entry, path,
                                'possibly missing audio link: ' + candidate,
                                not(arguments.path))
            
                
        else:
            # candidates for Maren (only existing or soon to be published)
            if audios == [] and entry.get('Status', '') in ('Red-f', 'Red-f-blockiert', 'Red-f-Sammelbecken', 'Red-2', 'Red-1'):
                candidates = [ wb.text(s)
                        for s in form.findall('%(Schreibung)s' % wb.TAGS)
                        if not s.get('Typ', '').startswith('U')
                ]
            
                if candidates == []:
                    continue # may happen if only U forms in //Formangabe
                candidate = candidates[0] # consider only first item

                if not candidate in lemmas_todo:
                    for st, wc in wb.generate_stimuli(candidate, form):
                        print(st, candidate, wc, entry.get('Typ'), entry.get('Zeitstempel'), sep='\t')
        if arguments.generate == 'main':
            break

# 2nd pass: check for files that are not referenced anywhere
for audiofile, _ in filter(lambda x: x[1] == False, audiofiles.items()):
    wb.report(None, None, 'Missing audio reference for '+audiofile, not(arguments.path))
