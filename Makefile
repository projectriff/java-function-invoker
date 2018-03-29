.PHONY: test docs verify-docs
COMPONENT = java-function-invoker
NAME = java

test:
	./mvnw test -B

docs:
	RIFF_INVOKER_PATHS=$(NAME)-invoker.yaml riff docs -d docs -c "init $(NAME)"
	RIFF_INVOKER_PATHS=$(NAME)-invoker.yaml riff docs -d docs -c "create $(NAME)"
	$(call embed_readme,init,$(NAME))
	$(call embed_readme,create,$(NAME))

define embed_readme
    $(shell cat README.md | perl -e 'open(my $$fh, "docs/riff_$(1)_$(2).md") or die "cannot open doc"; my $$doc = join("", <$$fh>) =~ s/^#/##/rmg; print join("", <STDIN>) =~ s/(?<=<!-- riff-$(1) -->\n).*(?=\n<!-- \/riff-$(1) -->)/\n$$doc/sr' > README.$(1).md; mv README.$(1).md README.md)
endef

verify-docs: docs
	git diff --exit-code -- docs
	git diff --exit-code -- README.md
