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


SpaceRead {
  var
    <>tree,
    <>linemap,
    <>sounds,
    <>lineSize,

    // algorithm state
    index,
    time,
    times,
    indentTime,
  
    // algorithm by-iteration variables
    line,
    indent,
    lastIndent,
  
    // preprocess state
    <>polyphony = 1, // gets auto-adjusted
    simulatedWrites,

    // not used by algorithm, recorded for use by other objects
    <>indentTimes,
    <>tags,
    <>length
  ;

  *new {
    arg tree, linemap;
    ^super.newCopyArgs(tree, linemap).init;
  }

  init {
    lineSize = 0;
    indentTimes = List[0];
    this.pre;
  }
  
  pre {
    
    this.initNumeric;

    tree.parse({
      arg arg_line, arg_indent, arg_lastIndent;
      
      line = arg_line;
      indent = arg_indent;
      lastIndent = arg_lastIndent;
      
      // Tagging. Not used for algorithm, see detectTag
      if (this.detectTag) {
        this.stripTag;
      };
      
      if(this.determine) {
        this.prePauseRecord;

        this.convert;
        
        if (line.size > lineSize) {
          lineSize = line.size;
        };
        
        this.record;
      };
    });

  
    polyphony = times.select({arg time; time > 0}).size;
  }

  initNumeric {
    index = 0;
    time = 0;
    indentTime = 0;
    //times = Array.fill(polyphony, 0);
    times = [0];
    tags = IdentityDictionary[];
  }

  isIndentOdd {
    var isOdd = indent % 2 == 1;
    ^isOdd;
  }

  hasIndentIncreased {
    var hasIncreased = indent > lastIndent ;
    ^hasIncreased;
  }

  hasIndentDecreased {
    var hasDecreased = indent < lastIndent;
    ^hasDecreased;
  }

  setIndentTime {
    indentTime = times.maxItem;
  }

  recordIndentTime {
    if (indentTime > indentTimes[indentTimes.size-1]) {
      indentTimes.add(indentTime);
    };
  }

  setIndex {
    //index = times.minIndex; // Spread out; original rudimentary
    //index = times.indexOf(times.select({arg time; time <= indentTime;}).maxItem); // Use shortest distance
    index = times.detectIndex({arg t; t <= indentTime;}); // Use first available
    //[\index, index, times, indentTime].postln;
  }

  setTime {
    if (index.isNil) {
      index = times.size;
      times=times.add(0);
    };
    time = times[index];
  }

  isDrop {
    var isDrop = time > indentTime;
    ^isDrop;
  }

  isIndentEven {
    var isEven = indent % 2 == 0;
    ^isEven;
  }

  determine {
    //[indent, lastIndent,((lastIndent - indent).abs * 0.5).round,indentTimes].postln;
    if (this.isIndentOdd, {
    
      //[\odd, index, sounds].postln;

      // Odd indent does parallelization, so we figure out
      // which channel to use
      
      // Keep track of indentTime by indent level
      // No note of a higher indent can come sooner than this
      if (this.hasIndentIncreased) {
        this.setIndentTime;
        this.recordIndentTime;
      };
      
      this.setIndex;
      this.setTime;

      if (this.isDrop, {
        (this.class.name + "dropped note" + line).postln;
        ^false;
      });
    });

    if (this.isIndentEven, {
      //[\even, index, sounds].postln;
      if (this.hasIndentDecreased) {
        this.setIndentTime;
        this.recordIndentTime;
      };
    });
    if (line.isNil) {
      ^false;
    };
    if (line == 0) {
      ^false;
    };
    if (line[0] == 0) {
      ^false;
    };
    if (line[1] == 0) {
      ^false;
    };
    
    ^true; 
  }

  toNumeric {

    this.initNumeric;

    tree.parse({
      arg arg_line, arg_indent, arg_lastIndent;
      line = arg_line;
      indent = arg_indent;
      lastIndent = arg_lastIndent;
  
      // Tagging. Not used for algorithm, see detectTag
      if (this.detectTag) {
        this.recordTag;
        this.stripTag;
      };

      if (this.determine) {
        //// Good, we figured out which channel we can use from
        //// indentation. Now insert the note.
 
        this.prePause;
        this.prePauseRecord;

        this.convert;
        this.pad;
        this.write;
        this.record;
 
        // Must keep this debug line!
        
        //[index,linemap.convertToSymbolic(line),times].postln;

      };
      
    });

    length = times.maxItem;
    
    // Implicit end tag
    // Unlike length, this is overridden if there is already an
    // end tag in the SpaceTracker file
    //this.recordTag(\end, length);

    // this.endPause;

    this.close;
  
    ^sounds;
  }

  endPause {
    // seems unecessary, should be re-enabled if need to
    // fill up all channels equally with pauses resurfaces.
    // I think it was just a workaround for back when the
    // ugen output last sample rather than zero if beyond length
    // TODO: delete if no need comes up, or document the need
    var d;
    times.do {|t,i|
      d = length - t;
      if (d > 0) {
        sounds[i].writeData(FloatArray.fill(sounds[i].numChannels, 0).put(0, d));
      };
    };
  }

  pad {
    if (line.size < sounds[index].numChannels) {
      line = line.addAll(Array.fill(sounds[index].numChannels-line.size, 0));
    }
  }

  prePauseRecord {
    if (times[index] < indentTime) {
      times[index] = indentTime;
    }
  }

  prePause {
    // Insert pre-pause if necessary
    // Parallel, so relative to indentTime when parallel started
    // Fill up with pause
    if (times[index] < indentTime) {
      // [\prepause, times[index], indentTime].postln;
      sounds[index].writeData(FloatArray.fill(sounds[index].numChannels, 0).put(0, indentTime-times[index]));
    };
  }

  write {
    // Insert main line
    sounds[index].writeData(line);
  }

  convert {
    line = linemap.convertToNumeric(line);
  }

  record {
    times.atInc(index, line[0]);
  }

  close {
    sounds.do({
      arg sound;
      sound.close;
    });
  }

  // Tagging: A single alphanumeric word
  // on a line is considered a zero length
  // pause for the purposes of the algorithm,
  // but the time of the tag will be stored
  // for further use by other objects.
     
  detectTag {
    ^line.class == Symbol;
  }

  recordTag {
    //(this.class.asString ++ $: + tag.asString + "recorded at" + times[index]).postln;
    if (false == tags.includesKey(line)) {
      tags.put(line, times[index]);
    }{
      (this.class.asString+"Warning: Ignoring tag '"++line++"' already encountered in '"++tree.path++"'").postln;
    }
  }

  stripTag {
    line = 0;
  }

}

SpaceReadError : Error {
}

