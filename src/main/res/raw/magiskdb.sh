db_sepatch() {
  magiskpolicy --live 'create magisk_file' 'attradd magisk_file mlstrustedobject' \
  'allow * magisk_file file *' 'allow * magisk_file dir *' \
  'allow magisk_file * filesystem associate'
}

db_clean() {
  local USERID=$1
  local DIR="/sbin/.core/db-${USERID}"
  umount -l /data/user*/*/*/databases/su.db $DIR $DIR/*
  rm -rf $DIR
  [ "$USERID" = "*" ] && rm -f /data/adb/magisk.db
}

db_init() {
  ADB_CONTEXT=`/system/bin/ls -dZ /data/adb | awk '{print $1}'`
  chcon u:object_r:magisk_file:s0 /data/adb
  chmod 777 /data/adb
}

db_restore() {
  chcon $ADB_CONTEXT /data/adb
  chmod 700 /data/adb
}

db_setup() {
  local USER=$1
  local USERID=$(($USER / 100000))
  local DIR=/sbin/.core/db-${USERID}
  mkdir -p $DIR
  touch $DIR/magisk.db
  mount -o bind /data/adb/magisk.db $DIR/magisk.db
  rm -f /data/adb/magisk.db-journal
  chcon u:object_r:magisk_file:s0 $DIR $DIR/*
  chmod 700 $DIR
  chown $USER.$USER $DIR
  chmod 666 $DIR/*
}
