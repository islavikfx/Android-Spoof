#!/system/bin/ruby


require 'json'
require 'base64'
require 'digest'


class Engine
  x = "1.0.0"
  def initialize
    @logs = []
  end
  def isRoot
    uid = `id -u`.strip
    if uid == "0"
      true
    else
      false
    end
  end
  def MagiskFramework
    mask = `which magisk 2>/dev/null`.strip
    if !mask_path.empty?
      mask_ver = `magisk -V 2>/dev/null`.strip
      true
    else
      false
    end
  end
end


if __FILE__ == $0
    # todo: Ruby init.
end