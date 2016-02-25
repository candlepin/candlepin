#!/usr/bin/env ruby
#
# Script to create an account with a large number of pools, and bind a
# separate entitlement for each to a specific consumer. The time required
# for each bind as well as some additional steps in the process is printed
# to stdout.
#
# Can be run and re-run against a typical dev deployment without any arguments.

require  "../client/ruby/candlepin_api"
require  "../client/ruby/hostedtest_api"
require 'pp'

require 'benchmark'

include HostedTest

ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin"
HOST = "localhost"
PORT = 8443

def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000)}"
end

def time_rand from = 0.0, to = Time.now
  Time.at(from + rand * (to.to_f - from.to_f))
end

def wait_for_job(job_id, timeout_seconds)
  states = ['FINISHED', 'CANCELED', 'FAILED']
  wait_interval = 0.005 # seconds
  total_taken = 0
  while total_taken < timeout_seconds
    sleep wait_interval
    total_taken += wait_interval
    status = @cp.get_job(job_id)
    if states.include? status['state']
      return
    end
  end
end

# from http://burgestrand.se/articles/quick-and-simple-ruby-thread-pool.html
class Pool
  def initialize(size)
    @size = size
    @jobs = Queue.new
    @pool = Array.new(@size) do |i|
      Thread.new do
        Thread.current[:id] = i
        catch(:exit) do
          loop do
            job, args = @jobs.pop
            job.call(*args)
          end
        end
      end
    end
  end

  def schedule(*args, &block)
    @jobs << [block, args]
  end

  def shutdown
    @size.times do
      schedule { throw :exit }
    end

    @pool.map(&:join)
  end
end

@cp = Candlepin.new(ADMIN_USERNAME, ADMIN_PASSWORD, nil, nil, HOST, PORT)

owner = @cp.create_owner random_string("consumertest")
puts "Created owner: #{owner['key']}"

prod_attrs = {}
(0..20).each do |i|
  prod_attrs[random_string("attr")] = random_string("val")
end
prod_attrs['multi-entitlement'] = "yes"
product1 = @cp.create_product(owner['key'], random_string(), random_string(),
  {:attributes => prod_attrs})

ent_count = ARGV.shift.to_i
thread_count = ARGV.shift.to_i
if ent_count == 0
  ent_count = 50
end
if thread_count == 0
  thread_count = 5
end

puts "running tests with ent_count: #{ent_count} and thread_count: #{thread_count}"

all_provided_products = [random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string()]
all_provided_products.each do |pid|
  @cp.create_product(owner['key'], pid, pid, {})
end

Benchmark.bm (10) do |x|
  x.report("Creating #{ent_count} pools:") {
    (1..ent_count).each do |i|
      start_date = Date.parse(time_rand(Time.local(2000, 1, 1), Time.now).to_s)
      end_date = Date.parse(time_rand(Time.now, Time.local(2050, 1, 1)).to_s)
      create_pool_and_subscription(owner['key'], product1['id'], 1, all_provided_products.sample(8), '', '12345', nil, start_date, end_date, true)
    end
  }
end

Benchmark.bm (10) do |x|
  x.report("Refresh:") {
    @cp.refresh_pools(owner['key'])
  }
end

pool_and_quantities = []
pools = @cp.list_owner_pools(owner['key'])

threaded_pool_and_quantities = []

(0..thread_count-1).each do |count|
  threaded_pool_and_quantities[count] = []
end

count = 0
pools.each do |pool|
  pool_and_quantities.push({'poolId' => pool['id'], 'quantity' => 1})
  threaded_pool_and_quantities[count].push({'poolId' => pool['id'], 'quantity' => 1})
  count = (count + 1) % thread_count
end

org_admin_username = random_string("orgadmin")
org_admin_password = 'password'
@cp.create_user(org_admin_username, org_admin_password, true)
org_admin_cp = Candlepin.new(org_admin_username, org_admin_password)
facts = {
    "distributor_version" => "sat-6.0",
    "satellite_version" => "6.0",
    "system.certificate_version" => "3.0"
}
consumer = org_admin_cp.register(random_string('dummyconsumer'), "system",
  nil, facts, nil, owner['key'])
puts "Created consumer: id = #{consumer['id']}, uuid = #{consumer['uuid']}"
consumer2 = org_admin_cp.register(random_string('dummyconsumer'), "system",
  nil, facts, nil, owner['key'])
consumer3 = org_admin_cp.register(random_string('dummyconsumer'), "system",
  nil, facts, nil, owner['key'])
consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'],
  HOST, PORT)
consumer_cp2 = Candlepin.new(nil, nil, consumer2['idCert']['cert'], consumer2['idCert']['key'],
  HOST, PORT)
consumer_cp3 = Candlepin.new(nil, nil, consumer3['idCert']['cert'], consumer3['idCert']['key'],
  HOST, PORT)

puts "Grabbing #{ent_count} entitlements"

#################################
#  non-concurrent, synchronous, singular bind
#################################
puts
puts
puts
puts "TEST:Singular Single thread bind sync"
time_start_single = Time.now
time_end_single = nil
pools.each do |pool|
  consumer_cp.consume_pool(pool['id'])
end
time_end_single = Time.now
puts "single bind of #{ent_count} pools started at #{time_start_single}"
puts "single bind of #{ent_count} pools ended at #{time_end_single}"
puts "TEST RESULT:single bind of #{ent_count} pools in one thread took #{time_end_single - time_start_single} seconds"

consumer_cp.revoke_all_entitlements()

#################################
#  concurrent, asynchronous, singular bind
#################################
puts
puts
puts
puts "TEST:Singular bind async"

start = Time.now
fn2187 = Time.now

statuses = []
Benchmark.bm (10) do |x|
  pools.each do |pool|
    status = @cp.consume_pool(pool['id'], {:async => true, :uuid => consumer2['uuid']})
    statuses.push(status)
  end
end

statuses.each do |status|
  wait_for_job(status['id'], 20)
end
fn2187 = Time.now
puts "Singular bind of #{ent_count} pools async started at #{start}"
puts "Singular bind of #{ent_count} pools ended at #{fn2187}"
puts "TEST RESULT:Singular bind of #{ent_count} pools async took #{fn2187 - start} seconds"

puts "TEST:Singular bind async - job time"
puts "TEST RESULT:Query to compute job time of single bind async"
puts "select sum(totaltime) from (select extract (milliseconds from (finishtime-starttime))  as totaltime from cp_job
where id in ("
statuses.each do |status|
  puts "'#{status['id']}',"
  #start = DateTime.parse(status['startTime']).to_time
end
puts ")) t"

puts "TEST singular bind async - number of compliance checks"
puts "select count(*) from cp_job where id like 'consumer_compliance%'"
puts " and targetid = '#{consumer2['uuid']}'"


consumer_cp2.revoke_all_entitlements()

#################################
#  concurrent, synchronous, singular bind
#################################
puts
puts
puts
puts "TEST Singular Multi thread bind sync"
time_start = Time.now
thread_pool = Pool.new(thread_count)
pools.each do |pool|
    thread_pool.schedule do
        consumer_cp.consume_pool(pool['id'])
    end
end
thread_pool.shutdown
time_stop = Time.now
puts "single bind of #{ent_count} pools started at #{time_start}"
puts "single bind of #{ent_count} pools ended at #{time_stop}"
puts "TEST RESULT:single bind of #{ent_count} pools in #{thread_count} threads took #{time_stop - time_start} seconds"

consumer_cp.revoke_all_entitlements()

#################################
#  non-concurrent, asynchronous, batch bind
#################################
puts
puts
puts
puts "TEST:Batch Single thread bind async"
time_start_batch = Time.now
time_end_batch = nil
result = nil
Benchmark.bm (10) do |x|
  x.report("Binding Batch:") {
    status = consumer_cp.consume_pools(pool_and_quantities)
    wait_for_job(status['id'], 20)
    result = @cp.get_job(status['id'], true)
  }
  time_end_batch = Time.now
end
puts "Batch bind of #{ent_count} pools started at #{time_start_batch}"
puts "Batch bind of #{ent_count} pools ended at #{time_end_batch}"
puts "TEST RESULT:Batch bind of #{ent_count} pools in one thread took #{time_end_batch - time_start_batch} seconds"

puts
puts
puts
puts "TEST:Batch Single thread bind async - job time"
puts "TEST RESULT:Query to compute job time of single thread batch bind"
puts "select extract (milliseconds from (finishtime-starttime))  as totaltime from cp_job
where id = '#{result['id']}'"

consumer_cp.revoke_all_entitlements()


#################################
#  concurrent, asynchronous, batch bind
#################################
puts
puts
puts

puts "TEST:Batch Multi thread bind async"

statuses = []
time_start_batch = Time.now
time_end_batch = nil
result = nil

thread_pool = Pool.new(thread_count)
threaded_pool_and_quantities.each do |pool_and_quantities|
    thread_pool.schedule do
        status = consumer_cp3.consume_pools(pool_and_quantities)
        statuses.push(status)
    end
end
thread_pool.shutdown

statuses.each do |status|
  wait_for_job(status['id'], 20)
end

time_end_batch = Time.now
puts "Batch bind of #{ent_count} pools started at #{time_start_batch}"
puts "Batch bind of #{ent_count} pools ended at #{time_end_batch}"
puts "TEST RESULT:Batch bind of #{ent_count} pools in one thread took #{time_end_batch - time_start_batch} seconds"

puts
puts
puts
puts "TEST:Batch bind Multi thread bind async - job time"
puts "TEST RESULT:Query to compute job time of multi thread batch bind"
puts "select sum(totaltime) from (select extract (milliseconds from (finishtime-starttime))  as totaltime from cp_job
where id in ("
statuses.each do |status|
  puts "'#{status['id']}',"
end
puts ")) t"


puts "TEST singular bind single thread bind async - number of compliance checks"
puts "select count(*) from cp_job where id like 'consumer_compliance%'"
puts " and targetid = '#{consumer3['uuid']}'"

consumer_cp3.revoke_all_entitlements()

