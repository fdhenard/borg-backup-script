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
- optional manual initialization of a Borg repository

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

`bb backup prod` initializes a missing local repository with
`--encryption=none`. The repository's parent directory must already exist.
Initialize the repository manually first if you want encryption or use a
remote repository.

The production run creates a backup, then prunes old archives using the
7 daily / 4 weekly retention rules, then compacts the repository to reclaim
disk space. Each step runs only if the preceding step exits successfully
(exit code 0); a backup warning or error stops pruning and compaction.

`bb backup` previews which files would be backed up and skips compaction.
If the configured local repository path is missing, it skips the pruning
preview. The dry run skips repository initialization. When pruning is
previewed, the retention rules apply only to archives already in the repository.
   
## restore

Run these commands from the script checkout with `BORG_BB_CONFIG_PATH` set.
The `--to` option is required. Choose a new directory or an existing empty
directory. The script creates missing directories and rejects non-empty
destinations.

```sh
# Restore the newest archive
bb restore --to ~/restores/latest

# Restore a specific archive
bb restore --to ~/restores/january 2023-01-25T15-53-17

# Restore selected files or directories
bb restore --to ~/restores/selected latest-backup 'home/user/Documents' 'home/user/Pictures'
```

Use `bb repo-do list` for archive names and `bb repo-do list-files latest-backup`
for paths as stored in the archive. The restore preserves the archived
directory structure under the destination and prints its full path. If Borg
reports a warning or error, the destination may contain a partial restore.

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
