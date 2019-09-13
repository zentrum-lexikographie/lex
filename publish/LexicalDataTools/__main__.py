# encoding: utf8

from GenericDictionary import GenericDictionary
import OutputFormatter, Validator, Tokenizer

if __name__ == '__main__':
    
    import logging, optparse, datetime

    op = optparse.OptionParser('%prog [options] files')
    op.add_option('-t', '--tool',
            choices=('headword_generator', 'semantic_validator', 'tokenizer', ),
            metavar='TOOL',
            help='specify the tool to invoke')
    op.add_option('-i', '--input-format',
            choices=('dwdswb-xml', 'tei-xml', 'mediawiki-xml'),
            default='dwdswb-xml',
            metavar='[dwdswb-xml|tei-xml|mediawiki-xml]',
            help='specify the input format (default: dwdswb-xml)')
    op.add_option('-o', '--output-format',
            choices=('plain', 'lmf'),
            default='plain',
            metavar='[plain|lmf]',
            help='specify output format (default: plain)')
    op.add_option('-a', '--all',
            action='store_true', default=False,
            help='use all <tei:form> elements even if embedded inside <tei:sense> elements (useful for EtymWB and 1DWB headword generation)')
    op.add_option('-p', '--pedantic',
            action='store_true', default=False,
            help='account for @rend rendering hints')
    op.add_option('-d', '--debug',
            action='store_true',
            help='enable debug output')
    op.add_option('--mdname',
            action='store',
            default='',
            metavar='NAME',
            help='set the resource name (used in LMF output)')
    op.add_option('--mdversion',
            action='store',
            default='',
            metavar='VERSION',
            help='set the resource version number (used for LMF output)')
    op.add_option('--mddate',
            action='store',
            default=datetime.date.today().isoformat(),
            metavar='DATE',
            help='set the resource creation date (used for LMF output)')
    options, arguments = op.parse_args()

    logging.basicConfig(format='%(name)s %(levelname)s: %(message)s',
            level=logging.DEBUG if options.debug else logging.WARNING)

    if options.tool is None:
        pass

    elif options.tool == 'headword_generator':
        
        dictionary = GenericDictionary(arguments, options.input_format, embedded_forms=options.all)
        formatter = OutputFormatter.HeadwordListFormatter(dictionary)

        if options.output_format == 'plain':
            print formatter.plaintext().encode('utf8')
        elif options.output_format == 'lmf':
            print formatter.lmf(options)

    elif options.tool == 'semantic_validator':
        dictionary = GenericDictionary(arguments)
        validator = Validator.SemanticValidator(dictionary)
        validator.report()

    elif options.tool == 'tokenizer':
        dictionary = GenericDictionary(arguments)
        tokenizer = Tokenizer.Tokenizer(dictionary, pedantic=options.pedantic)
        tokens = tokenizer.tokenize()
        if tokens:
            print '\n'.join(tokens)

