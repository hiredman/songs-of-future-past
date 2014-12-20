# Songs of Future Past

SoFP is a dj for your
[MPD](http://mpd.wikia.com/wiki/Music_Player_Daemon_Wiki) setup. SoFP
monitors you MPD playlist and watches what songs you play after other
songs building a graph of which songs follow which and how often they
do. With the graph in hand SoFP can do weighted random walks (like a
markov chain) to add songs to your playlist.

## Usage

Download the sofp jar from http://thelibraryofcongress.s3.amazonaws.com/sofp-0.1.0-standalone.jar

```shell
MPD_HOST=mpdhost.local MPD_PORT=2344 java -jar ${SOFP_JAR}
```

SoFP persists data in a derby database in `$HOME/.sofp.db` 

The following is the actual command line I use to launch SoFP:
```shell
MPD_HOST=pug.local MPD_PORT=6600 java -server -Xmx32m -Xms32m -XX:+UseTLAB -XX:-PrintGC -XX:+DoEscapeAnalysis -XX:+AdjustConcurrency -XX:+UseThreadPriorities -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -Dfile.encoding=utf-8 -Djava.net.preferIPv4Stack=true -XX:+UseG1GC -jar sofp-0.1.0-SNAPSHOT-standalone.jar   
```

## License

Copyright © 2014 Kevin Downey

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
