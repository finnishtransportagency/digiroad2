echo "Starting oracle configuration";
DR2_DIR=`pwd`;
cd /tmp;
# FIXME: This is a bit slow
rm -rf digiroad2-oracle;
git clone git@github.com:finnishtransportagency/digiroad2-oracle.git;
cd $DR2_DIR;
cp -r /tmp/digiroad2-oracle/lib digiroad2-oracle/;
cp -r /tmp/digiroad2-oracle/conf digiroad2-oracle/;
echo "DONE";
