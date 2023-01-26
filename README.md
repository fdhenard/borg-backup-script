# Borg Instructions

[toc]

## prerequisites

- install [babashka](https://github.com/babashka/babashka#installation)
- config file 
    - .edn file
    
        ```clojure
        {:repo-path "/path/to/repo"
         :patterns-path "/path/to/borg_patterns.lst"
         :dir-to-backup "/path/to/backup"
        ```

    - set environment variable `BORG_BB_CONFIG_PATH`

## backup

- plug in ext hd
- this

   ```
   cd ~/dev/repos/borg-backup-script
   bb backup
   ```
   
## other commands

- list backups `bb repo-do list`
- compact `bb repo-do compact`
- delete 
	- dry run `bb delete-backup backup-name`
	- prod `bb delete-backup --prod backup-name`
