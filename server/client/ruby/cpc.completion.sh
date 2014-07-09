#
# cpc bash completion script

# we could in theory, try to autocomplete the method args
# we could get the list of users and autocomplete
# ditto for pools, etc


_cpc()
{
  local cur prev opts base
  COMPREPLY=()

  # find list of options and list of commands
  CMDS="$(cpc --commands | sed -ne "s|\s*\(\w*\).*|\1|p")"
  opts="$(cpc --help | grep -v "commands" | sed -ne "s|\s*\(\-\-\w*\).*|\1|p") -c --commands ${CMDS}"

  first=${COMP_WORDS[1]}
  cur="${COMP_WORDS[COMP_CWORD]}"
  prev="${COMP_WORDS[COMP_CWORD-1]}"

  COMPREPLY=($(compgen -W "${opts}" -- ${cur}))
  return 0
}

complete -F _cpc cpc
