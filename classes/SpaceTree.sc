/*
    SpaceTracker for SuperCollider 
    Copyright (c) 2013 - 2017 Carlo Capocasa. All rights reserved.
    https://capocasa.net

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

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
    <path,
    levels
  ;

  *new {
    arg path;
    ^super.newCopyArgs(path);
  }

  path_ {
    arg p;
    if (p.contains("\n")) {
      path = thisProcess.platform.defaultTempDir +/+ "spacetree" ++ 2147483647.rand;
      File.use(path, "w") {|f|
        f.write(p);
        ShutDown.add{ var d = path; File.delete(d) };
      }
    } {
      path = p;
    }
  }

  asArray {
    var levels;
    levels=Array.newClear(maxindent); // TODO: make this dynamic but go easy on the mallocs
    this.parse({
      arg line, indent, lastindent;
      if (indent > maxindent) {
        SpaceTreeError("Can only indent up to " ++ maxindent ++ " indentations").throw;
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
    if (File.exists(path) == false) {
      SpaceTreeError(path + "does not exist").throw;
    };
		lastindent = 0;
    file=File.open(path, "r");
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
    file = File.open(path, "a");
    file.write(line++"\n");
    file.close;
  }
}

SpaceTreeError : Error {
}
