#!/usr/bin/env ruby

require_relative "bloom_filters"

class NameGenerator
  @@adj1 = %w(
    antsy          batty          beastly        beefy          bodily         bubbly
    bulky          burly          cheeky         chilly         chubby         cloudy
    clumsy         corny          costly         courtly        cowardly       crabby
    creepy         crinkly        crumbly        curly          dastardly      deadly
    deadly         deathly        disorderly     dizzy          early          elderly
    fancy          filthy         floppy         friendly       frilly         frothy
    funny          fuzzy          gaudy          ghastly        giddy          goodly
    gravelly       greasy         grisly         gritty         grubby         grumpy
    happy          healthy        heavanly       hilly          holy           homely
    hungry         icy            itchy          jittery        jolly          juicy
    jumpy          kindly         knurly         lazy           leisurely      likely
    lively         lonely         lovely         lowly          lucky          manly
    mannerly       measly         melancholy     miserly        misty          moody
    muddy          nasty          naughty        nutty          oily           only
    orderly        panicky        pearly         pebbly         petty          pimply
    portly         prickly        puny           ratty          saintly        salty
    scaly          scary          scholarly      scrawny        shaggy         shaky
    shapely        shiny          sickly         silky          silly          skinny
    slatternly     slimy          slippery       slovenly       sly            smarmy
    smelly         smoggy         soggy          sparkly        spicy          spindly
    sprightly      spritely       squiggly       stately        steady         steely
    sticky         stormy         surly          swanky         tasty          teeny
    testy          timely         treacly        tricky         ugly           ungainly
    unlikely       unruly         unsightly      wacky          weary          wily
    witty          wobbly         womanly        woolly         worldly        wriggly
    wrinkly        yummy          zany           zippy
  )

  @@adj2 = %w(
    abrupt         acidic         adorable       aggressive     agitated       alert
    aloof          amiable        amused         annoyed        anxious        appalling
    appetizing     arrogant       ashamed        attractive     average        bewildered
    biting         bitter         bland          blushing       bored          brave
    bright         broad          charming       cheerful       clean          clear
    clueless       colorful       colossal       combative      condemned      confused
    convincing     convoluted     courageous     crooked        cruel          cumbersome
    curved         cynical        dangerous      dashing        decayed        deceitful
    deep           defeated       defiant        delicious      delightful     depraved
    depressed      despicable     determined     diminutive     disgusted      distinct
    distraught     distressed     disturbed      drab           drained        dull
    eager          ecstatic       elated         elegant        emaciated      enchanting
    energetic      enormous       envious        excited        extensive      exuberant
    fantastic      fierce         flat           fluttering     foolish        frantic
    fresh          frightened     gentle         gigantic       glamorous      gleaming
    glorious       gorgeous       graceful       grieving       grotesque      handsome
    helpful        helpless       high           hollow         horrific       huge
    hurt           ideal          immense        intrigued      irate          irritable
    jealous        joyous         kind           large          lethal         little
    livid          loose          ludicrous      macho          mammoth        maniacal
    massive        melted         miniature      minute         mistaken       mortified
    motionless     mysterious     narrow         nervous        nonchalant     nutritious
    obedient       oblivious      obnoxious      odd            outrageous     perfect
    perplexed      petite         plain          pleasant       poised         pompous
    precious       proud          pungent        quaint         quizzical      reassured
    relieved       repulsive      responsive     ripe           robust         rotten
    rotund         rough          round          sarcastic      scant          scattered
    selfish        shallow        sharp          short          small          smiling
    smooth         smug           solid          sore           sour           sparkling
    splendid       spotless       square         stale          steep          stout
    straight       strange        strong         stunning       successful     succulent
    superior       sweet          tart           tender         tense          terrible
    thankful       thick          thoughtful     tight          trite          troubled
    uneven         upset          uptight        vast           vexed          victorious
    virtuous       vivacious      vivid          whimsical      whopping       wicked
    wonderful      worried        zealous
  )

  def initialize(suffixes, rand = nil)
    @prng = rand != nil ? rand : Random.new
    @bf = ScaleableBloomFilter.new()

    @suffixes = suffixes
    @max_name_count = @@adj1.length * @@adj2.length * @suffixes.length
  end

  def generate_name()
    adj1_index = @prng.rand(@@adj1.length)
    adj1_offset = 0
    adj2_index = @prng.rand(@@adj2.length)
    adj2_offset = 0
    noun_index = @prng.rand(@suffixes.length)
    noun_offset = 0

    while true
      adj1 = @@adj1[adj1_index]
      adj2 = @@adj2[adj2_index]
      noun = @suffixes[noun_index]

      name = adj1.capitalize + adj2.capitalize + noun.slice(0).upcase + noun.slice(1, noun.length)

      if @bf.test(name)
        if adj1_offset < @@adj1.length - 1
          adj1_index = (adj1_index + (adj1_offset += 1)) % @@adj1.length
        elsif adj2_offset < @@adj2.length - 1
          adj2_index = (adj2_index + (adj2_offset += 1)) % @@adj2.length
          adj1_offset = 0
        elsif noun_offset < @suffixes.length - 1
          noun_index = (noun_index + (noun_offset += 1)) % @suffixes.length
          adj1_offset = 0
          adj2_offset = 0
        else
          # We've exhausted every possible combination. Uh oh.
          raise "All possible names likely exhausted (generated: #{@bf.size()}, max: #{@max_name_count})"
        end

        next
      end

      break
    end
    @bf.add(name)

    return name
  end

  def generated?(name)
    return @bf.test(name)
  end
end
