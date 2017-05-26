# scala-tweets-analyzer
Simple scala script to analyze tweets activity for a given user.

Written in scala.

### Example output

![Example output](https://github.com/OleksandrBezhan/scala-tweets-analyzer/blob/master/example-output.png)

```bash
Usage: tweets-analyzer [options]

  -h, --help              
  -l, --limit N           limit the number of tweets to retrieve (default=1000)
  -n, --name screen_name  target twitter handle screen_name
```

### Libraries used
- [Twitter4s](https://github.com/DanielaSfregola/twitter4s) for Twitter API
- [Scopt](https://github.com/jstrachan/scopt) for arguments parsing

### Building for your OS
 
 ```
 # zip
 sbt universal:packageBin
 
 # tar
 sbt universal:packageZipTarball
 
 # dmg
 sbt universal:packageOsxDmg
 ```
