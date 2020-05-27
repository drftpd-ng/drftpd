# drftpd v3 upgrade to v4
Python script to upgrade v3 (JSON) style user files to v4 (JSON) style user files.
Also produces a groups.properties file to be run against a java tool (WIP)

# Arguments
*  -d, --debug                   Enable debug output  [default: False]
*  -u, --userdata-dir TEXT       The drftpd v3 json userdata base dir
                                [default: userdata; required]

*  -e, --exec                    Actually execute space actions  [default:
                                False]

*  -g, --group-output-file TEXT  This file contain the input for creating all
                                missing groups for v4  [default:
                                groups.properties]

*  --help                        Show this message and exit.

# Typical use:
* Copy this directory's contents to runtime/master/
* Download file to your base dir (where userdata exists)
* run in dry-run mode: python3 migrate-v3-v4.py (resolve any missing libraries)
* run in execute mode: python3 migrate-v3-v4.py -e (generates a groups.properties)
* Make executable: chmod 755 *.sh
* compile java: ./compile.sh
* Create groups: ./run.sh (Creates drftpd json group objects based on groups.properties)
