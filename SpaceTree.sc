/*

A SpaceTree is a data format to represent nested trees of data.
Its use is similar to YAML and is partly inspired by it.

It is unique in that it has been further simplified and uses
only alphanumeric characters and indents with one space.
It is best demonstrated with an example:

foo
 bar
 baz
 qux
foobar
 barfoo
  bazqux
qux

parses to

[
  \foo, [
    \baz,
    \qux
  ],
  \foobar, [
    \barfoo, [
      bazqux
    ]
  ],
  \qux
]


*/


SpaceTree {
  *parse {
    arg filename,maxindent=64; // TODO: make this dynamic but go easy on the mallocs
    var file,line,levels,indent,lastindent,line_as_integer;
    levels=Array.newClear(maxindent);
    lastindent = 0;
    file=File.open(filename, "r");
    
    while ({ (line=file.getLine).notNil }) {

      indent = 0;
      while ({line[indent] == $ }) {
        indent = indent + 1;
      };

      if (indent > maxindent) {
        throw("Can only indent up to " ++ maxindent ++ " indentations");
      };

      line = line.stripWhiteSpace;
      if (""==line, {
        line = nil;
      },{
        line_as_integer = line.asInteger;
        line = if ( line_as_integer > 0, line_as_integer, line.asSymbol );
      });

      case
      { indent < lastindent } {
        for (lastindent-1, indent, {
          arg i;
          levels[i] = levels[i].add(levels[i+1]);
        });
      }
      { indent == lastindent } {
      }
      { indent > lastindent } {
        levels[indent] = [];
      };
      levels[indent]=levels[indent].add(line);
      lastindent = indent;
    };
    ^levels[0];
  }
}

