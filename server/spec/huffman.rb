class HuffNode
  attr_accessor :weight, :symbol, :left, :right, :parent

  def initialize(params = {})
    @weight   = params[:weight]  || 0
    @symbol   = params[:symbol]  || ''
    @left     = params[:left]    || nil
    @right    = params[:right]   || nil
    @parent   = params[:parent]  || nil
  end

  def walk(&block)
    walk_node('', &block)
  end

  def walk_node(code, &block)
    yield(self, code)
    @left.walk_node(code + '0', &block) unless @left.nil?
    @right.walk_node(code + '1', &block) unless @right.nil?
  end

  def leaf?
    @symbol != ''
  end

  def internal?
    @symbol == ''
  end

  def root?
    internal? and @parent.nil?
  end

end

class NodeQueue
  attr_accessor :nodes, :huffman_root

  def initialize(list)
    frequencies = {}
    list.each do |c|
      frequencies[c] ||= 0
      frequencies[c] += 1
    end
    @nodes = []
    frequencies.each do |c, w|
      @nodes << HuffNode.new(:symbol => c, :weight => w)
    end
    generate_tree
  end

  def find_smallest(not_this)
    smallest = nil
    for i in 0..@nodes.size - 1
      if i == not_this
        next
      end
      if smallest.nil? or @nodes[i].weight < @nodes[smallest].weight
        smallest = i
      end
    end
    smallest
  end


  def generate_tree
    while @nodes.size > 1
      node1 = self.find_smallest(-1)
      node2 = self.find_smallest(node1)
      hn1 = @nodes[node1]
      hn2 = @nodes[node2]
      new = merge_nodes(hn1, hn2)
      @nodes.delete(hn1)
      @nodes.delete(hn2)
      @nodes.concat(Array.new(1,new))
    end
    @huffman_root = @nodes.first
  end

  def merge_nodes(node1, node2)
    left = node1
    right = node2
    node = HuffNode.new(:weight => left.weight + right.weight, :left => left, :right => right)
    left.parent = right.parent = node
    node
  end

end

class HuffmanEncoding
  attr_accessor :root, :lookup, :input, :output

  def initialize(input)
    @input = input
    @root = NodeQueue.new(input).huffman_root
#    @output = encode_list(input)
  end

  def lookup
    return @lookup if @lookup
    @lookup = {}
    @root.walk do |node, code|
      @lookup[code] = node.symbol if node.leaf?
    end
    @lookup
  end

  def encode(entry)
    self.lookup.invert[entry] || ""
  end

  def decode(code)
    self.lookup[code] || ""
  end

  def encode_list(list)
    code = ''
    list.each do |c|
      code += encode(c)
    end
    code
  end

  def decode_string(code)
    code = code.to_s
    string = ''
    subcode = ''
    code.each_char do |bit|
      subcode += bit
      unless decode(subcode).nil?
        string += decode(subcode)
        subcode = ''
      end
    end
    string
  end

  def to_s
    @output
  end

  def [](char)
    encode(char)
  end
end
