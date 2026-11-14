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

`bb backup prod` creates a backup, then prunes old archives using the
7 daily / 4 weekly retention rules, then compacts the repository to reclaim
disk space. Each step runs only if the preceding step exits successfully
(exit code 0); a backup warning or error stops pruning and compaction.

`bb backup` previews backup creation and pruning with `--dry-run` and skips
compaction. Because the preview does not create an archive, its pruning
preview uses only the archives already in the repository.
   
## other commands

- list backups `bb repo-do list`
- list files in the latest backup `bb repo-do list-files latest-backup`
- list files in a specific backup `bb repo-do list-files 2023-01-25T15-53-17`
  - `latest-backup` selects the newest archive by its timestamp; an empty repository reports an error.
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
# show files in the newest backup
bb repo-do list-files latest-backup
# show files in a specific backup
bb repo-do list-files 2023-01-25T15-53-17
borg list <repo-path>::<datetime>
borg info <repo-path>::<datetime>
```
