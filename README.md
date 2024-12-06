# Borg Instructions

[toc]

## prerequisites

- install [babashka](https://github.com/babashka/babashka#installation)
- config file 
    - .edn file
    
        ```clojure
        {
            :repo-path "/path/to/repo"
            :patterns-path "/path/to/borg_patterns.lst"
            :dir-to-backup "/path/to/backup"
        }
        ```

    - set environment variable `BORG_BB_CONFIG_PATH`
- initial create of borg repo

  ```shell
  $ borg init --encryption=none /path/to/repo
  ```

## backup

- plug in ext hd
- this

   ```
   cd ~/dev/repos/borg-backup-script
   bb backup # this is a dry run - shows what will happen
   bb backup prod
   ```
   
## other commands

- list backups `bb repo-do list`
- compact `bb repo-do compact`
- delete 
	- dry run `bb delete-backup backup-name`
	- prod `bb delete-backup --prod backup-name`

## commonly used plain borg commands

### prereq - show repo path

- `$ cat $BORG_BB_CONFIG_PATH`
- look at `:repo-path` property

### common commands

```sh
# show backup names (datetime)
bb repo-do list
borg list <repo-path>::<datetime>
borg info <repo-path>::<datetime>
```
