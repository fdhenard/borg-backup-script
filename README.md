# Borg Instructions

[toc]

## backup

- plug in ext hd
- this

   ```
   cd dev/repos/borg-backup-script
   bb backup
   ```
   
## other commands

- list backups `bb repo-do list`
- compact `bb repo-do compact`
- delete 
	- dry run `bb delete-backup backup-name`
	- prod `bb delete-backup --prod backup-name`
