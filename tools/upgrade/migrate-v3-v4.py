#!/usr/bin/python3

import os
import sys
import time
import json
from json import JSONDecodeError

# Click library (command line arguments)

try:
    import click
    from configparser import ConfigParser
    from datetime import datetime
except ImportError as ie:
    print('Please install missing libraries: {}'.format(ie.name))
    sys.exit(0)

assert sys.version_info >= (3, 0)

__NAME = 'dr-user-upgrade'
__AUTHOR = 'Mike--'
__VERSION = '0.1'
__LICENSE = 'Apache License 2.0'

class_replacements = {
    'org.drftpd.usermanager.encryptedjavabeans.EncryptedBeanUser':
        'org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUser',
    'org.drftpd.usermanager.javabeans.BeanUser':
        'org.drftpd.master.usermanager.javabeans.BeanUser',
    'org.drftpd.dynamicdata.Key': 'org.drftpd.common.dynamicdata.Key',
    'org.drftpd.plugins.stats.metadata.StatsUserData': 'org.drftpd.statistics.master.metadata.StatsUserData',
    'org.drftpd.commands.UserManagement': 'org.drftpd.master.commands.usermanagement.UserManagement',
    'org.drftpd.commands.request.metadata.RequestUserData': 'org.drftpd.request.master.metadata.RequestUserData',
    'org.drftpd.commands.nuke.metadata.NukeUserData': 'org.drftpd.master.commands.nuke.metadata.NukeUserData',
    'org.drftpd.util.HostMask': 'org.drftpd.common.util.HostMask',
    'org.drftpd.plugins.sitebot.UserDetails': 'org.drftpd.master.sitebot.UserDetails',
}
group_attrs = ['groupslots', 'leechslots', 'minratio', 'maxratio']


class Messages:
    ERROR = 100
    WARN = 200
    INFO = 300
    DEBUG = 400

    def __init__(self, level=INFO, gl_logfile=None):
        self.level = level
        self.gl_logfile = gl_logfile

    # noinspection PyMethodMayBeStatic
    def hline(self, level=INFO):
        if level <= self.level:
            click.echo('{} >>'.format("-" * 60))

    def send_message(self, message, level=INFO):
        if level <= self.level:

            prepend = '[{}]'.format(time.strftime("%a %d %b %Y %T", time.gmtime()))
            if level <= self.WARN:
                prepend += '[ERROR]'
            if self.INFO > level > self.ERROR:
                prepend += '[WARN ]'
            if self.DEBUG > level > self.WARN:
                prepend += '[INFO ]'
            if level > self.INFO:
                prepend += '[DEBUG]'

            message = '{} {}'.format(prepend, message)

            click.echo(message)


@click.command()
@click.option('--debug', '-d', required=False, default=False, is_flag=True,
              help='Enable debug output', show_default=True)
@click.option('--userdata-dir', '-u', required=True, default='userdata',
              help='The drftpd v3 json userdata base dir', show_default=True)
@click.option('--exec', '-e', required=False, default=False, is_flag=True,
              help='Actually execute space actions', show_default=True)
@click.option('--group-output-file', '-g', required=False, default='groups.properties',
              help='This file contain the input for creating all missing groups for v4', show_default=True)
def cli(**kwargs):
    debug = kwargs['debug']
    userdata = os.path.join(os.path.dirname(__file__), kwargs['userdata_dir'])
    group_output_file = kwargs['group_output_file']
    dryrun = not kwargs['exec']
    if debug:
        msg = Messages(Messages.DEBUG)
    else:
        msg = Messages()

    users_dir = os.path.join(userdata, 'users', 'javabeans')
    users_dir_backup = os.path.join(users_dir, 'backups')
    groups_dir = os.path.join(userdata, 'groups', 'javabeans')

    msg.send_message('Script started with input: userdata: [{}], dryrun: [{}]'.format(userdata, dryrun))
    msg.send_message('users_dir: {}, groups_dir: {}'.format(users_dir, groups_dir), msg.DEBUG)
    msg.send_message('Starting sanity checks', msg.DEBUG)
    if not os.path.isdir(userdata):
        msg.send_message('Userdata directory [{}] does not exist'.format(userdata))
        sys.exit(1)

    if not os.path.isdir(users_dir):
        msg.send_message('Users directory [{}] does not exist'.format(users_dir))
        sys.exit(1)

    if os.path.isdir(groups_dir):
        msg.send_message('It looks like the groups directory already exists, checking if empty'.format(groups_dir))
        if os.listdir(groups_dir):
            msg.send_message('Groups directory [{}] is not empty, it would mean we already have v4.0 based files'.
                             format(groups_dir))
            sys.exit(1)
        else:
            msg.send_message('Groups directory [{}] is empty, good'.format(groups_dir))
    else:
        if dryrun:
            msg.send_message('Would have created groups dir [{}]'.format(groups_dir))
        else:
            os.makedirs(groups_dir)

    if os.path.isdir(users_dir_backup):
        msg.send_message('Backup directory [{}] already exists, has the conversion already run?'.
                         format(users_dir_backup))
        sys.exit(1)

    if dryrun:
        msg.send_message('Would have created users backup dir [{}]'.format(users_dir_backup))
    else:
        os.makedirs(users_dir_backup)

    files = os.listdir(users_dir)
    for file in files:
        if os.path.isdir(os.path.join(users_dir, file)):
            continue

        file_ext = os.path.splitext(file)[1][1:]
        msg.send_message('Found user file [{}] with extension [{}]'.format(file, file_ext), msg.DEBUG)

        # We expect only json at this point
        if file_ext.lower() != 'json':
            msg.send_message('Found a user file that does not have the json extension: [{}], something is wrong'.
                             format(file))
            msg.send_message('Note: we do not support the conversion of drftpd based xml user files')
            sys.exit(1)

        try:
            # Test if the file is valid json
            with open(os.path.join(users_dir, file), 'r') as rfile:
                data = rfile.read()

            _ignore = json.loads(data)
        except JSONDecodeError:
            msg.send_message('We are unable to read json file [{}]'.format(file))
            sys.exit(1)

    # All good, let's convert user files and get a dict of groups
    user_count = 0
    groups = {}
    user_name = None
    for file in files:
        src_path = os.path.join(users_dir, file)
        if os.path.isdir(src_path):
            continue

        dst_path = os.path.join(users_dir_backup, file)
        with open(src_path, 'r') as rfile:
            data = rfile.read()

        json_data = json.loads(data)
        is_gadmin = False
        prim_group = None

        for key in json_data.keys():
            value = json_data[key]
            msg.send_message('Key: [{}:{}]'.format(key, value), msg.DEBUG)

            # The type of user manager used
            if key == '_username':
                user_name = value
            if key == '@type':
                if value in class_replacements:
                    json_data[key] = class_replacements[value]
                else:
                    msg.send_message('Found unknown user manager type: [{}:{}]'.format(key, value))
                    sys.exit(1)

            # Hostmasks
            if key == '_hostMasks':
                new_value = []
                for entry in value:
                    if entry['@type'] in class_replacements:
                        entry['@type'] = class_replacements[entry['@type']]
                    else:
                        msg.send_message('Found unknown hostmask class: [{}]'.format(entry['@type']))
                        sys.exit(1)

                    # Add the modified entry to the new set
                    new_value.append(entry)

                # Overwrite the json data with new set
                json_data[key] = new_value

            # The keyedmap structure ... Serialized as @keys => [{'@type': 'org.drftpd.dynamicdata.Key',
            # '_key': 'logins', '_owner': 'org.drftpd.plugins.stats.metadata.StatsUserData'}, {'@type':
            # 'org.drftpd.dynamicdata.Key', '_key': 'maxsimup', '_owner': 'org.drftpd.commands.UserManagement'}]
            # @items': [{'@type': 'int', 'value': 1414}, {'@type': 'int', 'value': 3}]
            if key == '_data':
                if len(json_data['_data']['@keys']) != len(json_data['_data']['@items']):
                    msg.send_message('Found incorrect serialization of the KeyedMap struct, '
                                     'keys and items count do not match')
                    sys.exit(1)

                map_keys = []
                map_items = None
                for key2 in value:
                    if key2 == '@keys':
                        # all keys in array form
                        for key3 in value[key2]:
                            new_array_entry = {}
                            # single array entry consists of a dict
                            for key4 in key3:
                                if key4 == '@type':
                                    if key3[key4] in class_replacements:
                                        new_array_entry[key4] = class_replacements[key3[key4]]
                                    else:
                                        msg.send_message('Found unknown key class: [{}:{}]'.
                                                         format(key4, key3[key4]))
                                        sys.exit(1)
                                elif key4 == '_owner':
                                    if key3[key4] in class_replacements:
                                        new_array_entry[key4] = class_replacements[key3[key4]]
                                    else:
                                        msg.send_message('Found unknown _owner class: [{}:{}]'.
                                                         format(key4, key3[key4]))
                                        sys.exit(1)
                                else:
                                    new_array_entry[key4] = key3[key4]
                            map_keys.append(new_array_entry)

                    elif key2 == '@items':
                        # Nothing to do
                        map_items = value[key2]
                    else:
                        msg.send_message('Found incompatible keyedmap format as this [{}] is unsupported'.format(key2))
                        sys.exit(1)

                json_data[key] = {'@keys': map_keys, '@items': map_items}

            # We might need to create the group
            if key == '_group':
                prim_group = value
                if value not in groups:
                    groups[value] = {}

            # Check for additional groups
            if key == '_groups':
                for group in value:
                    if group.lower() == 'gadmin':
                        is_gadmin = True
                    else:
                        if group not in groups:
                            groups[group] = {}

        # Handle group admin stuff
        if is_gadmin:
            msg.send_message('Handling group admin part for group [{}]'.format(prim_group), msg.DEBUG)
            i = 0
            group_data = {}
            for key in json_data['_data']['@keys']:
                value = json_data['_data']['@items'][i]
                if key['_key'] in group_attrs:
                    group_data[key['_key']] = value['value']
                    msg.send_message('[{}:{}] -> [{}]'.format(i, key['_key'], value['value']), msg.DEBUG)

                i += 1

            if len(group_data) != len(group_attrs):
                msg.send_message('Did not find the correct number of required arguments for the group attributes '
                                 '[Found:{},Expected:{}]'.format(len(group_data), len(group_attrs)))
                sys.exit(1)

            # Add the group admin user
            group_data['admin'] = user_name

            # Save group attributes
            groups[prim_group] = group_data

        # If we get here everything above was OK and we can write the new user file (after we create a backup)
        if dryrun:
            msg.send_message('Would have moved {} to {} and write new json object'.format(src_path, dst_path))

        else:
            os.rename(src_path, dst_path)
            with open(src_path, 'w') as wfile:
                wfile.write(json.dumps(json_data, indent=2, separators=(', ', ':')))

        user_count += 1

    if dryrun:
        msg.send_message('We would have upgraded {} users to v4 format'.format(user_count))
    else:
        msg.send_message('We upgraded {} users to v4 format'.format(user_count))

    # Prepare groups to be created
    if dryrun:
        msg.send_message('We would have written [{}] with content:\n{}'.format(group_output_file,
                                                                               json.dumps(groups, indent=2)))
    else:
        output = ""
        i = 1
        for group in groups:
            output += "group{}={}{}".format(i, group, os.linesep)
            for groupkey in groups[group]:
                output += "group{}.{}={}{}".format(i, groupkey, groups[group][groupkey], os.linesep)

            i += 1
        with open(group_output_file, 'w') as wfile:
            wfile.write(output)
        msg.send_message('We have written [{}] file'.format(group_output_file))
        msg.send_message('Use this file as input for the java Upgrade tool to create the missing groups')


# Start the program
if __name__ == '__main__':
    cli()
