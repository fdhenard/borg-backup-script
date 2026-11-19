# Borg Instructions

[toc]

## babashka script

- see [instructions here](https://github.com/fdhenard/borg-backup-script)

## straight borg

### init

should only need to be done once

    borg init --encryption=none /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020
    
### do the backup

#### prune

dry run

    borg prune -v --list --dry-run --keep-daily 7 --keep-weekly 4 /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020
    
actual prune

    borg prune -v --list --keep-daily 7 --keep-weekly 4 /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020

#### backup

dry run and list

    borg create --patterns-from ~/Documents/borg_patterns.lst --dry-run --list /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020::2023-01-25T11-01 ~/Documents
    
actual backup

    borg create --patterns-from ~/Documents/borg_patterns.lst --list /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020::2023-01-25T11-01 ~/Documents

### list contents of backup

    borg list /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020::<datetime>
    
also can list backups

    borg list /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020
    
### info about backup

    borg info /Volumes/Ext_HD_2TB/frank/borg-repo-mbpro2020::<date-time>