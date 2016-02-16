#!/usr/bin/env ruby

require 'digest/murmurhash'


class BloomFilter
  def initialize(slices, bits_per_slice)
    @slices = Array.new(slices, 0);
    @bits_per_slice = bits_per_slice

    bytes = (@bits_per_slice / 8).ceil
    slices.times do |i|
      @slices[i] = Array.new(bytes, 0)
    end

    @size = 0
  end

  def add(value)
    digest = Digest::MurmurHash3_x64_128.rawdigest(value)

    @slices.each_with_index do |slice, i|
      # Simulate a new hash by combining our two hashes with some magic. See the following for the
      # messy details: http://www.eecs.harvard.edu/~kirsch/pubs/bbbf/esa06.pdf
      g = (digest[0] + (i + 1) * digest[1]) % @bits_per_slice
      slice[(g / 8).floor] |= (1 << (g % 8))
    end

    @size += 1
  end

  def test(value)
    digest = Digest::MurmurHash3_x64_128.rawdigest(value)

    @slices.each_with_index do |slice, i|
      # See comment in .add for details on what this is doing.
      g = (digest[0] + (i + 1) * digest[1]) % @bits_per_slice
      if slice[(g / 8).floor] & (1 << (g % 8)) == 0
        return false
      end
    end

    return true
  end

  attr_reader :size
end

class ScaleableBloomFilter
  INITIAL_BITS_PER_SLICE = 128
  INITIAL_SLICES = 2
  GROWTH_RATE = 2
  DEFAULT_ERROR_RATE = 0.001
  ERROR_TIGHTENING_RATIO = 0.85

  def initialize(error_rate = DEFAULT_ERROR_RATE)
    @filters = Array.new(0)
    @active_filter = nil
    @max_elements = 0
    @max_error_rate = error_rate
  end

  def add(value)
    if @active_filter == nil || @active_filter.size() >= @max_elements
      add_filter()
    end

    @active_filter.add(value)
  end

  def test(value)
    @filters.each do |filter|
      if filter.test(value)
        return true
      end
    end

    return false
  end

  def size()
    count = 0
    @filters.each do |filter|
      count += filter.size()
    end

    return count
  end

  def add_filter()
    slice_count = get_slice_count(@filters.length)
    bits_per_slice = get_bits_per_slice(@filters.length)

    @max_elements = get_maximum_elements(@filters.length)
    @active_filter = BloomFilter.new(slice_count, bits_per_slice)
    @filters.push(@active_filter)
  end

  def get_slice_count(offset)
    k0 = Math.log2(@max_error_rate ** -1).ceil
    return (offset > 0 ? k0 + offset * Math.log2(ERROR_TIGHTENING_RATIO ** -1) : k0).ceil
  end

  def get_bits_per_slice(offset)
    return (INITIAL_BITS_PER_SLICE * GROWTH_RATE ** (offset - 1)).ceil
  end

  def get_maximum_elements(offset)
    slice_count = get_slice_count(offset)
    bits_per_slice = get_bits_per_slice(offset)

    return ((slice_count * bits_per_slice) * ((Math.log2(2) ** 2) / Math.log2(@max_error_rate).abs)).ceil
  end
end
