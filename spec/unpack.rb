#!/usr/bin/env ruby

# stdlib
require 'stringio'
require 'zlib'
require 'huffman'

module Unpack

  def inflate(data)
    Zlib::Inflate.inflate(data)
  end

  def deflate(data)
    Zlib::Deflate.deflate(data)
  end

  # there is not a difference for us, in these two
  def inflate2(data)
    zlib = Zlib::Inflate.new(15)
    buff = zlib.inflate(data)
    zlib.finish
    zlib.close
    buff
  end

  def load_dictionary(data)
    data.split("\x00")
  end

  def are_content_urls_present(value, urls)
    parent = prepare_from_blob(value)
    return content_in_tree(parent, urls)
  end

  def prepare_from_blob(byte_data)
    z_data_io = StringIO.new(byte_data)
    data = inflate(z_data_io.read())
    e_pos = deflate(data).bytesize()
    z_data_io.seek(e_pos)

    @sentinal = '*'
    string_trie = build_huffman_for_strings(load_dictionary(data))
    # print_trie(string_trie.root, 0)
    buf = z_data_io.read()
    path_root = build_path_nodes(buf, string_trie)
    path_root
  end

  def build_huffman_for_strings(strings)
    paths = []
    i = 1
    strings.each do |string|
      i.times { paths << string }
      i += 1
    end
    # add on sentinal string
    i.times { paths << @sentinal }
    HuffmanEncoding.new paths
  end

  def build_path_nodes(buf, string_trie)
    node_trie = nil
    node_dict = {}
    is_count = true
    multi_count = false
    byte_count = 0
    node_count = 0
    bit_list = ''
    buf.each_byte do |byte|
      if is_count # get number of nodes
        # print "Byte: #{byte}\n"
        if !multi_count and byte > 127
          multi_count = true
          byte_count = byte - 128
          # print "Byte count: #{byte_count}\n"
          next
        end
        if multi_count
          # print "Multicount\n"
          node_count = node_count << 8
          # print "Node count #{node_count}\n"
          node_count += byte
          # print "Node count #{node_count}\n"
          byte_count = byte_count - 1
          # print "Byte count #{byte_count}\n"
          multi_count = byte_count > 0
          # print "Multicount #{multi_count}\n"
          if multi_count
            next
          end
        else
          node_count += byte
        end
        # print "Node Count: #{node_count}\n"
        node_dict = []
        node_count.times { node_dict << Node.new() }
        node_trie = build_huffman_for_nodes(node_dict)
        # print_trie(node_trie.root, 0)
        is_count = false
      else # make bit list
          bit_list += get_bits(byte)
      end
    end
    #now take the node list and populate
    bit_start = 0
    bit_end = 0
    node_dict.each do |node|
      still_node = true
      while still_node and bit_end < bit_list.length do
        name_value = nil
        name_bits = Array.new(0)
        while name_value.to_s.empty? and still_node and bit_end < bit_list.length do
          name_bits = bit_list[bit_start..bit_end]
          bit_end += 1
          lookup_value = string_trie.decode(name_bits)
          if !lookup_value.to_s.empty?
            if lookup_value == @sentinal
              still_node = false
              bit_start = bit_end
              break
            end
            name_value = lookup_value
            bit_start = bit_end
          end
        end
        node_value = nil
        path_bits = ''
        while node_value.to_s.empty? and still_node and bit_end < bit_list.length do
          path_bits = bit_list[bit_start..bit_end]
          bit_end += 1
          lookup_value = node_trie.decode(path_bits)
          if !lookup_value.to_s.empty?
            node_value = lookup_value
            node.add_child(NodeChild.new({:name => name_value, :connection => node_value}))
            bit_start = bit_end
          end
        end
      end
    end
    node_dict[0]
  end

  def get_bits(byte)
    remain = byte
    return_val = ''
    return_val += get_bits_rec(remain, 7)
    return_val
  end

  def get_bits_rec(remain, power)
    if power < 0
      return
    end
    return_val = ''
    if remain > 2**power-1
      return_val = '1'
      new_val = get_bits_rec(remain-2**power, power-1)
    else
      return_val = '0'
      new_val = get_bits_rec(remain, power-1)
    end
    if not new_val.nil?
      return_val += new_val
    end
    return_val
  end

  def content_in_tree(parent, urls)
    urls.each do |url|
      chunks = url.split("/")
      if not can_find_path(chunks[1..-1], parent)
        return false
      end
    end
    true
  end

  def can_find_path(chunks, parent)
    if parent.children.length == 0 and chunks.length == 0
      return true
    end
    parent.children.each do |child|
      name = child.name.strip || child.name
      if name == chunks[0]
        return can_find_path(chunks[1..-1], child.connection)
      end
    end
    return false
  end

  def build_huffman_for_nodes(list)
    # parent doesn't have to go into the table
    i = 0
    expanded = []
    list.each do |node|
      i.times {expanded << node}
      i += 1
    end
    table = HuffmanEncoding.new expanded
  end

  def print_trie(hn, tab)
    nodeRep = ''
    idx = 0
    until idx == tab do
      nodeRep += "  "
      idx += 1
    end
    nodeRep += "Weight = ["
    nodeRep += hn.weight.to_s
    nodeRep += "]"
    nodeRep += ", Symbol = ["
    nodeRep += hn.symbol.to_s
    nodeRep += "]"

    puts nodeRep

    if !hn.left.nil?
      print_trie(hn.left, tab + 1)
    end
    if !hn.right.nil?
      print_trie(hn.right, tab + 1)
    end
  end

end

class Node
  attr_accessor :children

  def initialize()
    @children = Array.new()
  end

  def add_child(child)
    @children.concat(Array.new(1, child))
  end
end

class NodeChild
  attr_accessor :name, :connection

  def initialize(params = {})
    @name         = params[:name]  || 0
    @connection   = params[:connection]  || nil
  end
end
