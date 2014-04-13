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

  classvar
    whitespace = " \t\r\n",
    maxindent = 64 
  ;

  var
    <>filename,
    levels
  ;

  *new {
    arg filename;
    ^super.newCopyArgs(filename).init;
  }

  init {
  }

  asArray {
    var levels;
    levels=Array.newClear(maxindent); // TODO: make this dynamic but go easy on the mallocs
    this.parse({
      arg line, indent, lastindent;
      if (indent > maxindent) {
        throw("Can only indent up to " ++ maxindent ++ " indentations");
      };
      case
      { indent == lastindent } {
        // no change of indent level, do nothing
      }
      { indent < lastindent } {
        // indent got smaller
        for (lastindent-1, indent, {
          arg i;
          levels[i] = levels[i].add(levels[i+1]);
        });
      }
      { indent > lastindent } {
        // indent got larger
        levels[indent] = [];
      };
      levels[indent]=levels[indent].add(line);
    });
    ^levels[0];
  }

  parse {
    arg callback;
    var file,line,indent,lastindent,change;
    if (File.exists(filename) == false) {
      (filename + "does not exist").throw;
    };
		lastindent = 0;
    file=File.open(filename, "r");
    block {
      arg break;
      while ({ (line=file.getLine).notNil }) {
        indent = this.getIndent(line);
        line = this.parseLine(line);
        if (\break==callback.value(line, indent, lastindent)) {
          break.value;
        };
        lastindent = indent;
      };
    };
    file.close;
  }

  getIndent {
    arg line;
    var indent;
    indent = 0;
    while ({line[indent] == $ }) {
      indent = indent + 1;
    };
    ^indent;
  }

  parseLine {
    arg line;
    line=line.findRegexp("[^ \t\r\n]+").collect({|r|r[1]}).collect({|token|
      case
        {false == "[^0-9]".matchRegexp(token)} {token.asInteger;}
        {token.asSymbol}
      ;
    });
    ^switch(line.size, 0, nil, 1, line[0], line);
  }

  write {
    arg line, indent = 0;
    var file;
    line = String.fill(indent, $ ) ++ line.join(" ");
    file = File.open(filename, "a");
    file.write(line++"\n");
    file.close;
  }
}


