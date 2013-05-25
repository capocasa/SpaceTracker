/*

A SpaceTree is a data format to represent nested trees of data.
Its use is similar to YAML and is partly inspired by it.

It contains no special characters, and tree branches are designated
by a single space indent. Uppercase characters are discouraged.

It is designed to be easy to type while immersed in an artistic
process.

The name SpaceTree comes from the format being a data tree
designated by space characters. It is also an homage to 70s
counterculture, being designed for typing while spaced out,
perhaps contemplating the tree of life.

Example:

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
  *asArray {
    arg filename,maxindent=64; // TODO: make this dynamic but go easy on the mallocs
    var file,line,levels,indent,lastindent;
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
line.postln;
      line = line.stripWhiteSpace;
      line = case
        {line==""} {nil}
        {false == "[^0-9]".matchRegexp(line)} {line.asInteger;}
        {line.asSymbol}
      ;

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
      };      levels[indent]=levels[indent].add(line);
      lastindent = indent;
    };
    ^levels[0];
  }

  asAudioFile {
  }
}

