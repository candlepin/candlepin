#!/bin/bash

./bin/deploy -gmat
RUN_ID=$(uuidgen)
NUM_OWNERS=20

echo "RUN_ID ${RUN_ID}"
echo

for i in `seq 1 $NUM_OWNERS`
do
    echo "GENERATING ORG ${i}"
    ./client/ruby/cpc create_owner "test_owner-${i}-${RUN_ID}"
    ./client/ruby/cpc create_environment "test_owner-${i}-${RUN_ID}" $RANDOM "test_env-${i}"
    ./client/ruby/cpc create_user "test${i}-${RUN_ID}" "test" "true"
    ./client/ruby/gen_fake_report.py --hypervisors 3000 --guests 5 --format async >> ./client/ruby/data/owner$i-$RUN_ID.json

done

sudo truncate --size=0 /var/log/candlepin/*.log


python -c "raw_input('PRESS ENTER TO BEGIN CONSUMER CREATE JOBS')"

for i in `seq 1 $NUM_OWNERS`
do
    ./client/ruby/cpc hypervisor_update_file "test_owner-$i-$RUN_ID" "./client/ruby/data/owner$i-$RUN_ID.json" "true"
done

echo
echo "Done with initial submit"

python -c "raw_input('PRESS ENTER TO BEGIN CONSUMER UPDATE JOBS')"


for i in `seq 1 $NUM_OWNERS`
do
    ./client/ruby/cpc hypervisor_update_file "test_owner-$i-$RUN_ID" "./client/ruby/data/owner$i-$RUN_ID.json" "true"
done

echo
echo

tail -f /var/log/candlepin/candlepin.log | grep GOLDFISH

echo
