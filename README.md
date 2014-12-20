# Songs of Future Past

SoFP is a dj for your
[MPD](http://mpd.wikia.com/wiki/Music_Player_Daemon_Wiki) setup. SoFP
monitors you MPD playlist and watches what songs you play after other
songs building a graph of which songs follow which and how often they
do. With the graph in hand SoFP can do weighted random walks (like a
markov chain) to add songs to your playlist.

## Usage

```shell
MPD_HOST=mpdhost.local MPD_PORT=2344 java -jar ${SOFP_JAR}
```

SoFP persists data in a derby database in `$HOME/.sofp.db` 

## License

Copyright © 2014 Kevin Downey

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
