import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

export function MessageText({ content }: { content: string }) {
  return (
    <div className="message-text">
      <Markdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ children, href }) => (
            <a href={href} target="_blank" rel="noopener noreferrer">
              {children}
            </a>
          ),
          // Keep model-provided image URLs from fetching remote tracking resources.
          img: ({ alt }) => <span className="message-image-label">[图片：{alt || '图片'}]</span>,
          table: ({ children }) => (
            <div className="message-table-scroll">
              <table>{children}</table>
            </div>
          ),
        }}
      >
        {content}
      </Markdown>
    </div>
  );
}
